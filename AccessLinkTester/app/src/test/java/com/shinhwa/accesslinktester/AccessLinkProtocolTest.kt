package com.shinhwa.accesslinktester

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessLinkProtocolTest {
    @Test
    fun relay0OnPacketMatchesProtocol() {
        assertArrayEquals(
            byteArrayOf(0x02, 0x08, 0x00, 0x31, 0x31, 0x30, 0x30, 0x03),
            AccessLinkProtocol.relayControl(useRelay = 1, outputType = 1, time = 0)
        )
    }

    @Test
    fun allRelayOffPacketMatchesProtocol() {
        assertArrayEquals(
            byteArrayOf(0x02, 0x08, 0x00, 0x30, 0x30, 0x30, 0x30, 0x03),
            AccessLinkProtocol.relayControl(useRelay = 0, outputType = 0, time = 0)
        )
    }

    @Test
    fun getWiegandInputDataPacketMatchesProtocol() {
        assertArrayEquals(
            byteArrayOf(0x02, 0x04, 0x09, 0x03),
            AccessLinkProtocol.getWiegandInputData()
        )
    }

    @Test
    fun wiegand26AsciiHexPayloadDecodesCardFields() {
        val decoded = AccessLinkProtocol.decodeWiegandInput("00020246".encodeToByteArray())

        assertEquals(26, decoded.bitLength)
        assertEquals(1L, decoded.facilityCode)
        assertEquals(291L, decoded.cardNumber)
        assertEquals(65_827L, decoded.decimalCode)
        assertEquals(131_654L, decoded.raw32Decimal)
    }

    @Test
    fun wiegand34AsciiHexPayloadDecodesCardFields() {
        val decoded = AccessLinkProtocol.decodeWiegandInput("0002000246".encodeToByteArray())

        assertEquals(34, decoded.bitLength)
        assertEquals(256L, decoded.facilityCode)
        assertEquals(291L, decoded.cardNumber)
        assertEquals(16_777_507L, decoded.decimalCode)
    }

    @Test
    fun raw32PayloadDecodesDecimalCardNumber() {
        val decoded = AccessLinkProtocol.decodeRaw32Input(byteArrayOf(0x00, 0x02, 0x02, 0x46))

        assertEquals(32, decoded?.bitLength)
        assertEquals(131_654L, decoded?.raw32Decimal)
        assertEquals("32bit / Decimal 131654 / Data 00 02 02 46", decoded?.summary)
    }
}
