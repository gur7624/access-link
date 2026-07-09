package com.shinhwa.accesslinktester

object SerialPayloadCodec {
    private const val MAX_PAYLOAD_BYTES = 64

    fun parse(input: String, mode: SerialInputMode): SerialPayloadParseResult {
        return when (mode) {
            SerialInputMode.ASCII -> parseAscii(input)
            SerialInputMode.HEX -> parseHex(input)
        }
    }

    fun safeAscii(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            if (value in 0x20..0x7E) value.toChar().toString() else "."
        }
    }

    private fun parseAscii(input: String): SerialPayloadParseResult {
        val bytes = input.encodeToByteArray()
        if (bytes.isEmpty()) {
            return SerialPayloadParseResult.Error("데이터를 입력하세요.")
        }
        if (bytes.size > MAX_PAYLOAD_BYTES) {
            return SerialPayloadParseResult.Error("ASCII 데이터는 64바이트까지 전송할 수 있습니다.")
        }
        return SerialPayloadParseResult.Success(bytes)
    }

    private fun parseHex(input: String): SerialPayloadParseResult {
        val compact = input.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) {
            return SerialPayloadParseResult.Error("HEX 데이터를 입력하세요.")
        }
        if (compact.any { it.digitToIntOrNull(16) == null }) {
            return SerialPayloadParseResult.Error("HEX는 0-9, A-F만 입력할 수 있습니다.")
        }
        if (compact.length % 2 != 0) {
            return SerialPayloadParseResult.Error("HEX는 두 자리씩 입력해야 합니다.")
        }

        val bytes = compact.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        if (bytes.size > MAX_PAYLOAD_BYTES) {
            return SerialPayloadParseResult.Error("HEX 데이터는 64바이트까지 전송할 수 있습니다.")
        }
        return SerialPayloadParseResult.Success(bytes)
    }
}

enum class SerialInputMode {
    ASCII,
    HEX
}

sealed interface SerialPayloadParseResult {
    data class Success(val bytes: ByteArray) : SerialPayloadParseResult
    data class Error(val message: String) : SerialPayloadParseResult
}
