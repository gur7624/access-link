package com.shinhwa.accesslinktester

import org.junit.Assert.assertArrayEquals
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
}
