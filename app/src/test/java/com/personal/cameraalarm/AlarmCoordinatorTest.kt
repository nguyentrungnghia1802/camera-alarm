package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AlarmCoordinatorTest {
    private fun snapshot() = TriggerSnapshot(AlarmToken("x"), "camera", "n", "r", null, null, 0)
    @Test fun permissionMissingAndFailureLeaveIdle() = runTest {
        for (result in listOf(ScheduleResult.ExactAlarmPermissionMissing, ScheduleResult.Failed("boom"))) {
            val store = InMemoryAlarmStateStore()
            val scheduler = object : AlarmScheduler {
                override fun canScheduleExactAlarms() = false
                override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = result
                override fun cancel(token: AlarmToken) = Unit
            }
            val coordinator = AlarmCoordinator(Clock { 100 }, scheduler, store, { AlarmPolicy() })
            assertEquals(AlarmOutcome.SCHEDULE_FAILED, coordinator.onValidTrigger(snapshot()))
            assertEquals(AlarmState.Idle, store.read())
        }
    }
    @Test fun pendingPersistsBeforeScheduleAndStaleFireIgnored() = runTest {
        val store = InMemoryAlarmStateStore()
        var observed: AlarmState = AlarmState.Idle
        val scheduler = object : AlarmScheduler {
            override fun canScheduleExactAlarms() = true
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult {
                observed = kotlinx.coroutines.runBlocking { store.read() }
                assertEquals(1100, triggerAtEpochMs)
                return ScheduleResult.Scheduled
            }
            override fun cancel(token: AlarmToken) = Unit
        }
        val coordinator = AlarmCoordinator(Clock { 100 }, scheduler, store, { AlarmPolicy() })
        assertEquals(AlarmOutcome.SCHEDULED, coordinator.onValidTrigger(snapshot()))
        assertTrue(observed is AlarmState.Pending)
        coordinator.onExactAlarmFired(snapshot().copy(alarmToken = AlarmToken("stale")))
        assertTrue(store.read() is AlarmState.Pending)
        coordinator.onExactAlarmFired(snapshot())
        assertTrue(store.read() is AlarmState.Ringing)
    }
    @Test fun bootChangeClearsPending() = runTest {
        var boot = 1; var saved: Int? = null
        val delegate = InMemoryAlarmStateStore()
        val store = BootScopedAlarmStateStore(delegate, BootMarker { boot }, object : BootMarkerStore {
            override suspend fun read() = saved
            override suspend fun write(marker: Int) { saved = marker }
        })
        store.write(AlarmState.Pending(snapshot(), 1000))
        assertTrue(store.read() is AlarmState.Pending)
        boot = 2
        assertEquals(AlarmState.Idle, store.read())
    }
    @Test fun revokedPermissionDoesNotLeaveOldPendingSuppressingNewTrigger() = runTest {
        val store = InMemoryAlarmStateStore()
        store.write(AlarmState.Pending(snapshot(), 1000))
        val coordinator = AlarmCoordinator(Clock { 100 }, object : AlarmScheduler {
            override fun canScheduleExactAlarms() = false
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.ExactAlarmPermissionMissing
            override fun cancel(token: AlarmToken) = Unit
        }, store, { AlarmPolicy() })
        assertEquals(AlarmOutcome.SCHEDULE_FAILED, coordinator.onValidTrigger(snapshot().copy(alarmToken = AlarmToken("new"))))
        assertEquals(AlarmState.Idle, store.read())
    }
    @Test fun stopPendingCancelsOnlyItsTokenAndIsIdempotent() = runTest {
        val cancelled = mutableListOf<AlarmToken>()
        val store = InMemoryAlarmStateStore()
        val coordinator = AlarmCoordinator(Clock { 100 }, object : AlarmScheduler {
            override fun canScheduleExactAlarms() = true
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.Scheduled
            override fun cancel(token: AlarmToken) { cancelled += token }
        }, store, { AlarmPolicy() })
        coordinator.onValidTrigger(snapshot())
        coordinator.onStopRequested(AlarmToken("other"))
        assertTrue(store.read() is AlarmState.Pending)
        coordinator.onStopRequested(snapshot().alarmToken)
        coordinator.onStopRequested(snapshot().alarmToken)
        assertEquals(listOf(snapshot().alarmToken), cancelled)
        assertTrue(store.read() is AlarmState.Cooldown)
    }
}
