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
        return "$name / Data ${data.toHexString()}"
    }

    private fun Int.asciiDigit(): Byte {
        return ('0'.code + this).toByte()
    }
}

fun ByteArray.toHexString(): String {
    return joinToString(" ") { byte ->
        (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
    }
}
