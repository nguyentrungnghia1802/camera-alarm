package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.AlarmPolicy
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import com.personal.cameraalarm.permission.ReadinessState
import com.personal.cameraalarm.trigger.TriggerConfiguration
import com.personal.cameraalarm.ui.main.AppStatus
import org.junit.Assert.*
import org.junit.Test

class MainViewModelTest {
    @Test
    fun readinessStateDerivedStatuses() {
        val blockingNotReady = ReadinessState(
            notificationAccessGranted = false,
            listenerConnected = false,
            exactAlarmGranted = false,
            postNotificationsGranted = false,
            sourceConfigured = false,
            ruleConfigured = false,
            alarmVolumeNonZero = false
        )
        assertFalse(blockingNotReady.blockingReady)
        assertFalse(blockingNotReady.readyForMonitoring)

        val blockingReady = ReadinessState(
            notificationAccessGranted = true,
            listenerConnected = true,
            exactAlarmGranted = true,
            postNotificationsGranted = true,
            sourceConfigured = true,
            ruleConfigured = true,
            alarmVolumeNonZero = true
        )
        assertTrue(blockingReady.blockingReady)
        assertTrue(blockingReady.readyForMonitoring)
    }

    @Test
    fun effectiveStatusComputations() {
        val statusReady = AppStatus.READY
        val statusNeedsSetup = AppStatus.NEEDS_SETUP
        val statusAlarming = AppStatus.ALARMING

        assertNotEquals(statusReady, statusNeedsSetup)
        assertNotEquals(statusReady, statusAlarming)
    }
}
