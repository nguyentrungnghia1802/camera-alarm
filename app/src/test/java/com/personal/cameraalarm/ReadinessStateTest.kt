package com.personal.cameraalarm

import com.personal.cameraalarm.permission.ReadinessState
import org.junit.Assert.*
import org.junit.Test

class ReadinessStateTest {
    private val ready = ReadinessState(true, true, true, true, true, true, true)
    @Test fun blockingPermissionsAndConfigurationAreRequired() {
        assertTrue(ready.blockingReady)
        assertTrue(ready.readyForMonitoring)
        assertFalse(ready.copy(notificationAccessGranted = false).blockingReady)
        assertFalse(ready.copy(exactAlarmGranted = false).blockingReady)
        assertFalse(ready.copy(sourceConfigured = false).blockingReady)
        assertFalse(ready.copy(ruleConfigured = false).blockingReady)
    }
    @Test fun transientAndDegradedStatesStayDistinct() {
        assertTrue(ready.copy(listenerConnected = false).blockingReady)
        assertFalse(ready.copy(listenerConnected = false).readyForMonitoring)
        assertTrue(ready.copy(postNotificationsGranted = false).blockingReady)
        assertFalse(ready.copy(postNotificationsGranted = false).readyForMonitoring)
        assertTrue(ready.copy(alarmVolumeNonZero = false).blockingReady)
        assertFalse(ready.copy(alarmVolumeNonZero = false).readyForMonitoring)
    }
}
