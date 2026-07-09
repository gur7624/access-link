package com.shinhwa.accesslinktester

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WiegandPayloadCodecTest {
    @Test
    fun outputHexIgnoresWhitespace() {
        val result = WiegandPayloadCodec.parseOutputHex("00 02\n02 46")

        assertTrue(result is WiegandPayloadParseResult.Success)
        assertArrayEquals(
            byteArrayOf(0x00, 0x02, 0x02, 0x46),
            (result as WiegandPayloadParseResult.Success).bytes
        )
    }

    @Test
    fun outputHexRejectsOddLength() {
        val result = WiegandPayloadCodec.parseOutputHex("ABC")

        assertTrue(result is WiegandPayloadParseResult.Error)
        assertEquals("HEX는 두 자리씩 입력해야 합니다.", (result as WiegandPayloadParseResult.Error).message)
    }

    @Test
    fun outputHexRejectsInvalidCharacter() {
        val result = WiegandPayloadCodec.parseOutputHex("00 GG")

        assertTrue(result is WiegandPayloadParseResult.Error)
        assertEquals("HEX는 0-9, A-F만 입력할 수 있습니다.", (result as WiegandPayloadParseResult.Error).message)
    }

    @Test
    fun outputHexAccepts16Bytes() {
        val input = List(16) { "AA" }.joinToString(" ")
        val result = WiegandPayloadCodec.parseOutputHex(input)

        assertTrue(result is WiegandPayloadParseResult.Success)
        assertEquals(16, (result as WiegandPayloadParseResult.Success).bytes.size)
    }

    @Test
    fun outputHexRejects17Bytes() {
        val input = List(17) { "AA" }.joinToString(" ")
        val result = WiegandPayloadCodec.parseOutputHex(input)

        assertTrue(result is WiegandPayloadParseResult.Error)
        assertEquals("Wiegand 출력 데이터는 16바이트까지 전송할 수 있습니다.", (result as WiegandPayloadParseResult.Error).message)
    }

    @Test
    fun outputPacketWithParityMatchesProtocol() {
        val packet = AccessLinkProtocol.wiegandOut(0, byteArrayOf(0x00, 0x02, 0x02, 0x46))

        assertArrayEquals(
            byteArrayOf(0x02, 0x09, 0x02, 0x30, 0x00, 0x02, 0x02, 0x46, 0x03),
            packet
        )
    }

    @Test
    fun outputPacketWithoutParityMatchesProtocol() {
        val packet = AccessLinkProtocol.wiegandOut(1, byteArrayOf(0x00, 0x02, 0x02, 0x46))

        assertArrayEquals(
            byteArrayOf(0x02, 0x09, 0x02, 0x31, 0x00, 0x02, 0x02, 0x46, 0x03),
            packet
        )
    }
}
