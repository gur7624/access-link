package com.shinhwa.accesslinktester

object WiegandPayloadCodec {
    private const val MAX_OUTPUT_BYTES = 16

    fun parseOutputHex(input: String): WiegandPayloadParseResult {
        val compact = input.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) {
            return WiegandPayloadParseResult.Error("Wiegand HEX 데이터를 입력하세요.")
        }
        if (compact.any { it.digitToIntOrNull(16) == null }) {
            return WiegandPayloadParseResult.Error("HEX는 0-9, A-F만 입력할 수 있습니다.")
        }
        if (compact.length % 2 != 0) {
            return WiegandPayloadParseResult.Error("HEX는 두 자리씩 입력해야 합니다.")
        }

        val bytes = compact.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        if (bytes.size > MAX_OUTPUT_BYTES) {
            return WiegandPayloadParseResult.Error("Wiegand 출력 데이터는 16바이트까지 전송할 수 있습니다.")
        }
        return WiegandPayloadParseResult.Success(bytes)
    }
}

sealed interface WiegandPayloadParseResult {
    data class Success(val bytes: ByteArray) : WiegandPayloadParseResult
    data class Error(val message: String) : WiegandPayloadParseResult
}
