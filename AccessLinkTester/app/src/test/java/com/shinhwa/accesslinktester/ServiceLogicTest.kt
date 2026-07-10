package com.shinhwa.accesslinktester

import com.shinhwa.accesslinktester.model.CardOutcome
import com.shinhwa.accesslinktester.model.DoorConfig
import com.shinhwa.accesslinktester.model.RegisteredCard
import com.shinhwa.accesslinktester.model.ServiceSettings
import com.shinhwa.accesslinktester.model.evaluateCard
import com.shinhwa.accesslinktester.model.visibleToUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLogicTest {

    private val cards = listOf(
        RegisteredCard(key = "131654", name = "홍길동", addedAt = 0L)
    )
    private val doors = listOf(
        DoorConfig(index = 0, name = "정문", openSeconds = 3, enabled = true),
        DoorConfig(index = 1, name = "", openSeconds = 3, enabled = false)
    )

    // --- 카드 인증 판정: 자동개방 OFF ---

    @Test
    fun autoOpenOff_registeredCard_isTagWithName() {
        val outcome = evaluateCard(
            key = "131654", cardNumber = "131654",
            cards = cards, settings = ServiceSettings(autoOpenEnabled = false), doors = doors
        )
        assertTrue(outcome is CardOutcome.Tag)
        assertEquals("홍길동", (outcome as CardOutcome.Tag).personName)
    }

    @Test
    fun autoOpenOff_unregisteredCard_isTagWithoutName() {
        val outcome = evaluateCard(
            key = "999", cardNumber = "999",
            cards = cards, settings = ServiceSettings(autoOpenEnabled = false), doors = doors
        )
        assertTrue(outcome is CardOutcome.Tag)
        assertNull((outcome as CardOutcome.Tag).personName)
    }

    // --- 카드 인증 판정: 자동개방 ON ---

    @Test
    fun autoOpenOn_registeredCard_isGrantedWithVisibleTargets() {
        val settings = ServiceSettings(autoOpenEnabled = true, autoOpenDoorIndexes = setOf(0, 1))
        val outcome = evaluateCard("131654", "131654", cards, settings, doors)
        assertTrue(outcome is CardOutcome.Granted)
        outcome as CardOutcome.Granted
        assertEquals("홍길동", outcome.personName)
        // index 1 은 이름 없음/off 이므로 개방 대상에서 제외된다
        assertEquals(setOf(0), outcome.doorIndexes)
    }

    @Test
    fun autoOpenOn_unregisteredCard_isDenied() {
        val settings = ServiceSettings(autoOpenEnabled = true, autoOpenDoorIndexes = setOf(0))
        val outcome = evaluateCard("999", "999", cards, settings, doors)
        assertTrue(outcome is CardOutcome.Denied)
    }

    // --- PIN 검증 ---

    @Test
    fun pin_correct_passes() {
        assertTrue(AdminConfig.isValid("3919"))
    }

    @Test
    fun pin_wrong_fails() {
        assertFalse(AdminConfig.isValid("0000"))
        assertFalse(AdminConfig.isValid(""))
        assertFalse(AdminConfig.isValid("39190"))
    }

    // --- 문 노출 규칙 ---

    @Test
    fun door_hiddenWhenNameBlank() {
        assertFalse(DoorConfig(index = 0, name = "", openSeconds = 3, enabled = true).visibleToUser())
    }

    @Test
    fun door_hiddenWhenDisabled() {
        assertFalse(DoorConfig(index = 0, name = "정문", openSeconds = 3, enabled = false).visibleToUser())
    }

    @Test
    fun door_visibleWhenNamedAndEnabled() {
        assertTrue(DoorConfig(index = 0, name = "정문", openSeconds = 3, enabled = true).visibleToUser())
    }
}
