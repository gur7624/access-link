package com.shinhwa.accesslinktester

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialPayloadCodecTest {
    @Test
    fun asciiInputConvertsToBytes() {
        val result = SerialPayloadCodec.parse("ACCESS", SerialInputMode.ASCII)

        assertTrue(result is SerialPayloadParseResult.Success)
        assertArrayEquals("ACCESS".encodeToByteArray(), (result as SerialPayloadParseResult.Success).bytes)
    }

    @Test
    fun hexInputIgnoresWhitespace() {
        val result = SerialPayloadCodec.parse("41 42\n43", SerialInputMode.HEX)

        assertTrue(result is SerialPayloadParseResult.Success)
        assertArrayEquals(byteArrayOf(0x41, 0x42, 0x43), (result as SerialPayloadParseResult.Success).bytes)
    }

    @Test
    fun hexInputRejectsOddLength() {
        val result = SerialPayloadCodec.parse("ABC", SerialInputMode.HEX)

        assertTrue(result is SerialPayloadParseResult.Error)
        assertEquals("HEX는 두 자리씩 입력해야 합니다.", (result as SerialPayloadParseResult.Error).message)
    }

    @Test
    fun hexInputRejectsInvalidCharacter() {
        val result = SerialPayloadCodec.parse("41 ZZ", SerialInputMode.HEX)

        assertTrue(result is SerialPayloadParseResult.Error)
        assertEquals("HEX는 0-9, A-F만 입력할 수 있습니다.", (result as SerialPayloadParseResult.Error).message)
    }

    @Test
    fun hexInputAccepts64Bytes() {
        val input = List(64) { "AA" }.joinToString(" ")
        val result = SerialPayloadCodec.parse(input, SerialInputMode.HEX)

        assertTrue(result is SerialPayloadParseResult.Success)
        assertEquals(64, (result as SerialPayloadParseResult.Success).bytes.size)
    }

    @Test
    fun hexInputRejects65Bytes() {
        val input = List(65) { "AA" }.joinToString(" ")
        val result = SerialPayloadCodec.parse(input, SerialInputMode.HEX)

        assertTrue(result is SerialPayloadParseResult.Error)
        assertEquals("HEX 데이터는 64바이트까지 전송할 수 있습니다.", (result as SerialPayloadParseResult.Error).message)
    }

    @Test
    fun safeAsciiReplacesControlCharacters() {
        assertEquals("A.B", SerialPayloadCodec.safeAscii(byteArrayOf(0x41, 0x02, 0x42)))
    }
}
