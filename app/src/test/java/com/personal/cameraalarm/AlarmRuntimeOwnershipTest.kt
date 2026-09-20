package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.AlarmRuntimeOwnership
import com.personal.cameraalarm.alarm.AlarmStartDecision
import com.personal.cameraalarm.alarm.AlarmState
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.TriggerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRuntimeOwnershipTest {
    private val productionToken = AlarmToken("production")
    private val testToken = AlarmToken("test-active")
    private val trigger = TriggerSnapshot(
        productionToken, "camera.app", "key", "rule", "title", "preview", 1_000L
    )

    @Test
    fun pendingAndRingingProductionBlockTestAlarm() {
        assertFalse(AlarmRuntimeOwnership.canStartTest(AlarmState.Pending(trigger, 2_000L), null))
        assertFalse(AlarmRuntimeOwnership.canStartTest(AlarmState.Ringing(trigger, 2_000L), null))
        assertTrue(AlarmRuntimeOwnership.canStartTest(AlarmState.Idle, null))
    }

    @Test
    fun productionReplacesTestButTestNeverReplacesProduction() {
        assertEquals(
            AlarmStartDecision.REPLACE_ACTIVE_TEST,
            AlarmRuntimeOwnership.decideStart(testToken, productionToken, requestedIsTest = false)
        )
        assertEquals(
            AlarmStartDecision.REJECT,
            AlarmRuntimeOwnership.decideStart(productionToken, AlarmToken("test-new"), requestedIsTest = true)
        )
        assertEquals(
            AlarmStartDecision.REJECT,
            AlarmRuntimeOwnership.decideStart(productionToken, AlarmToken("production-2"), requestedIsTest = false)
        )
        assertEquals(
            AlarmStartDecision.ACCEPT,
            AlarmRuntimeOwnership.decideStart(productionToken, productionToken, requestedIsTest = false)
        )
    }

    @Test
    fun stopAlwaysPrefersRingingProductionToken() {
        assertEquals(
            productionToken,
            AlarmRuntimeOwnership.stopToken(AlarmState.Ringing(trigger, 2_000L), testToken)
        )
        assertEquals(testToken, AlarmRuntimeOwnership.stopToken(AlarmState.Idle, testToken))
    }
}
