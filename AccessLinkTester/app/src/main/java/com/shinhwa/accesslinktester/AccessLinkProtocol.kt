package com.shinhwa.accesslinktester

object AccessLinkProtocol {
    private const val STX: Byte = 0x02
    private const val ETX: Byte = 0x03

    const val CMD_SET_RELAY_CONTROL: Int = 0x00
    const val CMD_SET_SERIAL_SEND: Int = 0x01
    const val CMD_SET_WIEGAND_OUT: Int = 0x02
    const val CMD_GET_WIEGAND_INPUT_DATA: Int = 0x09
    const val CMD_GET_RECV_DATA_RS232: Int = 0x10
    const val CMD_GET_RECV_DATA_RS485: Int = 0x11
    const val CMD_GET_INPUT_PORT_0: Int = 0x12
    const val CMD_GET_INPUT_PORT_1: Int = 0x13

    fun relayControl(useRelay: Int, outputType: Int, time: Int): ByteArray {
        require(useRelay in 0..2) { "useRelay must be 0..2" }
        require(outputType in 0..1) { "outputType must be 0..1" }
        require(time in 0..99) { "time must be 0..99" }

        val data = byteArrayOf(
            useRelay.asciiDigit(),
            outputType.asciiDigit(),
            (time / 10).asciiDigit(),
            (time % 10).asciiDigit()
        )
        return packet(CMD_SET_RELAY_CONTROL, data)
    }

    fun serialSend(useSerialPort: Int, sendData: ByteArray): ByteArray {
        require(useSerialPort in 0..1) { "useSerialPort must be 0..1" }
        require(sendData.size in 1..64) { "sendData size must be 1..64" }
        return packet(CMD_SET_SERIAL_SEND, byteArrayOf(useSerialPort.asciiDigit()) + sendData)
    }

    fun wiegandOut(useParity: Int, wiegandData: ByteArray): ByteArray {
        require(useParity in 0..1) { "useParity must be 0..1" }
        require(wiegandData.size in 1..16) { "wiegandData size must be 1..16" }
        return packet(CMD_SET_WIEGAND_OUT, byteArrayOf(useParity.asciiDigit()) + wiegandData)
    }

    fun packet(command: Int, data: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..255) { "command must be 0..255" }
        val length = 4 + data.size
        require(length <= 255) { "packet length must be <= 255" }

        return byteArrayOf(STX, length.toByte(), command.toByte()) + data + ETX
    }

    fun describeIncoming(packet: ByteArray): String {
        if (packet.size < 4) return "짧은 수신 데이터: ${packet.toHexString()}"
        if (packet.first() != STX || packet.last() != ETX) return "원시 수신: ${packet.toHexString()}"

        val command = packet[2].toInt() and 0xFF
        val data = packet.copyOfRange(3, packet.size - 1)
        val name = when (command) {
            CMD_SET_RELAY_CONTROL -> "릴레이 응답"
            CMD_SET_SERIAL_SEND -> "시리얼 송신 응답"
            CMD_SET_WIEGAND_OUT -> "Wiegand 출력 응답"
            CMD_GET_WIEGAND_INPUT_DATA -> "Wiegand 입력"
            CMD_GET_RECV_DATA_RS232 -> "RS-232 수신"
            CMD_GET_RECV_DATA_RS485 -> "RS-485 수신"
            CMD_GET_INPUT_PORT_0 -> "Input 0"
            CMD_GET_INPUT_PORT_1 -> "Input 1"
            else -> "알 수 없는 명령 0x${command.toString(16).uppercase().padStart(2, '0')}"
        }
        if (command == CMD_GET_WIEGAND_INPUT_DATA) {
            return "$name / ${decodeWiegandInput(data).summary}"
        }
        return "$name / Data ${data.toHexString()}"
    }

    fun decodeWiegandInput(payload: ByteArray): WiegandInput {
        val bitLength = when (payload.size) {
            8 -> 26
            10 -> 34
            else -> null
        }
        val dataBytes = payload.asAsciiHexBytesOrSelf()
        val rawValue = dataBytes.fold(0L) { value, byte ->
            (value shl 8) or (byte.toLong() and 0xFF)
        }

        val decimalCode = when (bitLength) {
            26 -> (rawValue and 0x03FF_FFFFL) ushr 1
            34 -> (rawValue and 0x3_FFFF_FFFFL) ushr 1
            else -> null
        }
        val facilityCode = when (bitLength) {
            26 -> decimalCode?.ushr(16)?.and(0xFF)
            34 -> decimalCode?.ushr(16)?.and(0xFFFF)
            else -> null
        }
        val cardNumber = decimalCode?.and(0xFFFF)

        return WiegandInput(
            bitLength = bitLength,
            payloadHex = payload.toHexString(),
            dataHex = dataBytes.toHexString(),
            facilityCode = facilityCode,
            cardNumber = cardNumber,
            decimalCode = decimalCode,
            raw32Decimal = dataBytes.takeIf { it.size == 4 }?.fold(0L) { value, byte ->
                (value shl 8) or (byte.toLong() and 0xFF)
            }
        )
    }

    fun decodeRaw32Input(data: ByteArray): WiegandInput? {
        if (data.size != 4) return null
        val raw32Decimal = data.fold(0L) { value, byte ->
            (value shl 8) or (byte.toLong() and 0xFF)
        }
        return WiegandInput(
            bitLength = 32,
            payloadHex = data.toHexString(),
            dataHex = data.toHexString(),
            facilityCode = null,
            cardNumber = null,
            decimalCode = null,
            raw32Decimal = raw32Decimal
        )
    }

    private fun Int.asciiDigit(): Byte {
        return ('0'.code + this).toByte()
    }
}

data class WiegandInput(
    val bitLength: Int?,
    val payloadHex: String,
    val dataHex: String,
    val facilityCode: Long?,
    val cardNumber: Long?,
    val decimalCode: Long?,
    val raw32Decimal: Long?
) {
    val summary: String
        get() {
            val type = bitLength?.let { "${it}bit" } ?: "Unknown"
            val raw32Text = raw32Decimal?.let { " / 32bit Decimal $it" }.orEmpty()
            if (bitLength == 32 && raw32Decimal != null) {
                return "32bit / Decimal $raw32Decimal / Data $dataHex"
            }
            if (decimalCode == null || facilityCode == null || cardNumber == null) {
                return "$type / Raw $payloadHex$raw32Text"
            }
            return "$type / Card $cardNumber / FC $facilityCode / Decimal $decimalCode$raw32Text / Data $dataHex"
        }
}

private fun ByteArray.asAsciiHexBytesOrSelf(): ByteArray {
    val chars = map { it.toInt() and 0xFF }
    if (chars.isEmpty() || chars.size % 2 != 0) return this
    if (!chars.all { it in '0'.code..'9'.code || it in 'A'.code..'F'.code || it in 'a'.code..'f'.code }) {
        return this
    }
    val hex = chars.map { it.toChar() }.joinToString("")
    return ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.toHexString(): String {
    return joinToString(" ") { byte ->
        (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
    }
}
