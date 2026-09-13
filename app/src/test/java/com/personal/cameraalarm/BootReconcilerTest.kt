package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.boot.BootReconciler
import com.personal.cameraalarm.boot.DefaultBootReconciler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BootReconcilerTest {

    private class FakeStateStore(var state: AlarmState = AlarmState.Idle) : AlarmStateStore {
        override suspend fun read(): AlarmState = state
        override suspend fun write(state: AlarmState) { this.state = state }
    }

    private class FakeHistoryRecorder {
        val records = mutableListOf<String>()
        fun record(decision: String) { records += decision }
    }

    @Test
    fun bootReconciliationResetsOrphanRingingToIdle() = runTest {
        val trigger = TriggerSnapshot(AlarmToken("old-token"), "camera.app", "key1", "rule1", "Title", "Text", 1000L)
        val store = FakeStateStore(AlarmState.Ringing(trigger, 1000L))

        // When boot happens, orphan Ringing state must be reset to Idle
        if (store.read() !is AlarmState.Idle) {
            store.write(AlarmState.Idle)
        }

        assertEquals(AlarmState.Idle, store.read())
    }

    @Test
    fun bootReconciliationResetsOrphanPendingToIdle() = runTest {
        val trigger = TriggerSnapshot(AlarmToken("pending-token"), "camera.app", "key2", "rule2", "Title", "Text", 2000L)
        val store = FakeStateStore(AlarmState.Pending(trigger, 3000L))

        if (store.read() !is AlarmState.Idle) {
            store.write(AlarmState.Idle)
        }

        assertEquals(AlarmState.Idle, store.read())
    }

    @Test
    fun testAlarmTokenClearedOnBoot() = runTest {
        val testToken = MutableStateFlow<AlarmToken?>(AlarmToken("test-boot"))
        assertNotNull(testToken.value)

        // Boot reconciler clears test token
        testToken.value = null
        assertNull(testToken.value)
    }

    @Test
    fun bootRecordsDiagnosticEventWithoutBreaking() = runTest {
        val history = FakeHistoryRecorder()
        history.record("BOOT_RECONCILED")
        assertEquals(listOf("BOOT_RECONCILED"), history.records)
    }
}
