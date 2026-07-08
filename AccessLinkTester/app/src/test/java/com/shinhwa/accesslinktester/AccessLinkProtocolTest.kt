package com.shinhwa.accesslinktester

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun packetDecoderDecodesNormalPacket() {
        val decoder = ShalPacketDecoder()
        val results = decoder.accept(AccessLinkProtocol.getWiegandInputData())

        assertEquals(1, results.size)
        val packet = (results.single() as ProtocolDecodeResult.Packet).packet
        assertEquals(4, packet.length)
        assertEquals(AccessLinkProtocol.CMD_GET_WIEGAND_INPUT_DATA, packet.command)
        assertArrayEquals(byteArrayOf(), packet.data)
    }

    @Test
    fun packetDecoderWaitsForPartialPacket() {
        val decoder = ShalPacketDecoder()
        val packet = AccessLinkProtocol.relayControl(useRelay = 1, outputType = 1, time = 0)

        assertTrue(decoder.accept(packet.copyOfRange(0, 3)).isEmpty())

        val results = decoder.accept(packet.copyOfRange(3, packet.size))
        assertEquals(1, results.size)
        assertArrayEquals(packet, (results.single() as ProtocolDecodeResult.Packet).packet.raw)
    }

    @Test
    fun packetDecoderDecodesMultiplePacketsInOneBuffer() {
        val decoder = ShalPacketDecoder()
        val first = AccessLinkProtocol.getWiegandInputData()
        val second = AccessLinkProtocol.relayControl(useRelay = 2, outputType = 0, time = 0)

        val results = decoder.accept(first + second)

        assertEquals(2, results.size)
        assertArrayEquals(first, (results[0] as ProtocolDecodeResult.Packet).packet.raw)
        assertArrayEquals(second, (results[1] as ProtocolDecodeResult.Packet).packet.raw)
    }

    @Test
    fun packetDecoderRecoversFromGarbageBeforeStx() {
        val decoder = ShalPacketDecoder()
        val packet = AccessLinkProtocol.getWiegandInputData()

        val results = decoder.accept(byteArrayOf(0x55, 0x66) + packet)

        assertEquals(2, results.size)
        assertTrue((results[0] as ProtocolDecodeResult.Error).error is ProtocolError.GarbageBeforeStx)
        assertArrayEquals(packet, (results[1] as ProtocolDecodeResult.Packet).packet.raw)
    }

    @Test
    fun packetDecoderReportsInvalidLengthAndRecovers() {
        val decoder = ShalPacketDecoder()
        val valid = AccessLinkProtocol.getWiegandInputData()

        val results = decoder.accept(byteArrayOf(0x02, 0x03, 0x09) + valid)

        assertTrue(results.any { it is ProtocolDecodeResult.Error && it.error is ProtocolError.InvalidLength })
        assertArrayEquals(valid, (results.last() as ProtocolDecodeResult.Packet).packet.raw)
    }

    @Test
    fun packetDecoderReportsInvalidEtxAndRecovers() {
        val decoder = ShalPacketDecoder()
        val invalid = byteArrayOf(0x02, 0x04, 0x09, 0x00)
        val valid = AccessLinkProtocol.getWiegandInputData()

        val results = decoder.accept(invalid + valid)

        assertTrue(results.any { it is ProtocolDecodeResult.Error && it.error is ProtocolError.InvalidEtx })
        assertArrayEquals(valid, (results.last() as ProtocolDecodeResult.Packet).packet.raw)
    }

    @Test
    fun commandRouterReturnsProtocolErrorForUnknownCommand() {
        val packet = AccessLinkProtocol.decodePacket(byteArrayOf(0x02, 0x04, 0x7F, 0x03))

        val event = AccessLinkCommandRouter.route(packet)

        assertTrue(event is DeviceEvent.ProtocolErrorEvent)
        assertTrue((event as DeviceEvent.ProtocolErrorEvent).error is ProtocolError.UnknownCommand)
    }

    @Test
    fun commandRouterRoutesRs232Packet() {
        val packet = AccessLinkProtocol.decodePacket(
            byteArrayOf(0x02, 0x06, AccessLinkProtocol.CMD_GET_RECV_DATA_RS232.toByte(), 0x41, 0x42, 0x03)
        )

        val event = AccessLinkCommandRouter.route(packet)

        assertTrue(event is DeviceEvent.SerialReceived)
        val serialEvent = event as DeviceEvent.SerialReceived
        assertEquals(SerialPort.RS232, serialEvent.port)
        assertArrayEquals(byteArrayOf(0x41, 0x42), serialEvent.payload)
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
