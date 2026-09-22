package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.AlarmState
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.TriggerSnapshot
import com.personal.cameraalarm.boot.BootReconcilerDependencies
import com.personal.cameraalarm.boot.DefaultBootReconciler
import com.personal.cameraalarm.boot.ListenerRecoveryResult
import com.personal.cameraalarm.boot.NotificationListenerRecovery
import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.notification.ListenerStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReconcilerTest {
    private class FakeDependencies(initialState: AlarmState) : BootReconcilerDependencies {
        var state = initialState
        var testToken: AlarmToken? = AlarmToken("test-boot")
        var configAwaited = false
        var recoveryCalls = 0
        var historyCalls = 0
        val diagnostics = mutableListOf<String>()

        var configReady = true
        var recoveryResult = ListenerRecoveryResult.CONNECTED
        var outcome: com.personal.cameraalarm.boot.BootRecoveryOutcome? = null
        override suspend fun awaitConfigLoaded(): Boolean { configAwaited = true; return configReady }
        override suspend fun reconcileAlarm() = com.personal.cameraalarm.alarm.AlarmReconciliation("current boot", state)
        override fun monitoringEnabled() = true
        override fun exactAlarmCapable() = true
        override suspend fun recoverNotificationListener(): ListenerRecoveryResult {
            recoveryCalls++
            return recoveryResult
        }
        override suspend fun recordBootEvent(outcome: com.personal.cameraalarm.boot.BootRecoveryOutcome) {
            historyCalls++
            this.outcome = outcome
        }
        override fun recordDiagnostic(message: String) { diagnostics += message }
    }

    @Test
    fun productionReconcilerPreservesCurrentBootStateAndRecordsOutcome() = runTest {
        val trigger = TriggerSnapshot(
            AlarmToken("old-token"), "camera.app", "key", "rule", "Title", "Text", 1_000L
        )
        val dependencies = FakeDependencies(AlarmState.Ringing(trigger, 1_000L))

        DefaultBootReconciler(dependencies).reconcile()

        assertTrue(dependencies.configAwaited)
        assertTrue(dependencies.state is AlarmState.Ringing)
        assertEquals(AlarmToken("test-boot"), dependencies.testToken)
        assertTrue(dependencies.outcome!!.success)
        assertEquals(1, dependencies.recoveryCalls)
        assertEquals(1, dependencies.historyCalls)
    }

    @Test fun timeoutOrDisconnectedNeverReportsSuccess() = runTest {
        val dependencies = FakeDependencies(AlarmState.Idle)
        dependencies.configReady = false
        DefaultBootReconciler(dependencies).reconcile()
        assertTrue(!dependencies.outcome!!.success)
        assertTrue(dependencies.outcome!!.retry)
        dependencies.configReady = true
        dependencies.recoveryResult = ListenerRecoveryResult.DISCONNECTED
        DefaultBootReconciler(dependencies).reconcile()
        assertTrue(!dependencies.outcome!!.success)
        assertTrue(dependencies.outcome!!.retry)
    }
    @Test
    fun accessDeniedDoesNotRequestRebind() = runTest {
        val state = ListenerConnectionState()
        var requests = 0
        val result = NotificationListenerRecovery(
            accessGranted = { false }, connectionState = state,
            requestRebind = { requests++ }, recordDiagnostic = {}, wait = {}
        ).recover()

        assertEquals(ListenerRecoveryResult.ACCESS_DENIED, result)
        assertEquals(0, requests)
        assertEquals(ListenerStatus.DISCONNECTED, state.status.value)
    }

    @Test
    fun alreadyConnectedDoesNotRequestRebind() = runTest {
        val state = ListenerConnectionState().apply { connected() }
        var requests = 0
        val result = NotificationListenerRecovery(
            accessGranted = { true }, connectionState = state,
            requestRebind = { requests++ }, recordDiagnostic = {}, wait = {}
        ).recover()

        assertEquals(ListenerRecoveryResult.ALREADY_CONNECTED, result)
        assertEquals(0, requests)
    }

    @Test
    fun delayedConnectCompletesWithinBoundedRetry() = runTest {
        val state = ListenerConnectionState()
        var requests = 0
        var waits = 0
        val result = NotificationListenerRecovery(
            accessGranted = { true }, connectionState = state,
            requestRebind = { requests++ }, recordDiagnostic = {},
            wait = { waits++; state.connected() }
        ).recover()

        assertEquals(ListenerRecoveryResult.CONNECTED, result)
        assertEquals(1, requests)
        assertEquals(1, waits)
    }

    @Test
    fun failuresRemainBoundedAndEndDisconnected() = runTest {
        val state = ListenerConnectionState()
        val diagnostics = mutableListOf<String>()
        var requests = 0
        val result = NotificationListenerRecovery(
            accessGranted = { true }, connectionState = state,
            requestRebind = { requests++; error("notification manager unavailable") },
            recordDiagnostic = { diagnostics += it }, retryDelayMs = 1,
            maxAttempts = 3, wait = {}
        ).recover()

        assertEquals(ListenerRecoveryResult.DISCONNECTED, result)
        assertEquals(3, requests)
        assertEquals(ListenerStatus.DISCONNECTED, state.status.value)
        assertTrue(diagnostics.last().contains("after 3 attempts"))
    }
}
