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

    @Test fun cooldownSuppressesTriggersWithoutResettingAndAllowsAfterExpiry() = runTest {
        var currentTime = 1_000L
        val store = InMemoryAlarmStateStore()
        val scheduler = object : AlarmScheduler {
            override fun canScheduleExactAlarms() = true
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.Scheduled
            override fun cancel(token: AlarmToken) = Unit
        }
        val policy = AlarmPolicy(delayMs = 1_000, cooldownMs = 300_000) // 5 minutes
        val coordinator = AlarmCoordinator(Clock { currentTime }, scheduler, store, { policy })

        // 1. Initial trigger schedules alarm
        assertEquals(AlarmOutcome.SCHEDULED, coordinator.onValidTrigger(snapshot()))
        val ringingSnapshot = snapshot()
        coordinator.onExactAlarmFired(ringingSnapshot)
        assertTrue(store.read() is AlarmState.Ringing)

        // 2. Stop alarm at 1,000ms -> enters cooldown until 1,000 + 300,000 = 301,000ms
        coordinator.onStopRequested(ringingSnapshot.alarmToken)
        val cooldownState = store.read() as AlarmState.Cooldown
        assertEquals(301_000L, cooldownState.untilEpochMs)

        // 3. Trigger at 120,000ms (2 mins in) is suppressed due to cooldown
        currentTime = 120_000L
        val secondTrigger = snapshot().copy(alarmToken = AlarmToken("trigger-2"))
        assertEquals(AlarmOutcome.SUPPRESSED_COOLDOWN, coordinator.onValidTrigger(secondTrigger))

        // Cooldown deadline must NOT be reset by the intermediate trigger
        val stateAfterSuppression = store.read() as AlarmState.Cooldown
        assertEquals(301_000L, stateAfterSuppression.untilEpochMs)

        // 4. Trigger at 301,000ms (after cooldown expires) is permitted and schedules new alarm
        currentTime = 301_000L
        val thirdTrigger = snapshot().copy(alarmToken = AlarmToken("trigger-3"))
        assertEquals(AlarmOutcome.SCHEDULED, coordinator.onValidTrigger(thirdTrigger))
        assertTrue(store.read() is AlarmState.Pending)
    }

    @Test fun customCooldownWindowWorksCorrectly() = runTest {
        var currentTime = 0L
        val store = InMemoryAlarmStateStore()
        val scheduler = object : AlarmScheduler {
            override fun canScheduleExactAlarms() = true
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.Scheduled
            override fun cancel(token: AlarmToken) = Unit
        }
        val customSeconds = 120L // 120 seconds custom cooldown
        val policy = AlarmPolicy(delayMs = 1_000, cooldownMs = customSeconds * 1000L)
        val coordinator = AlarmCoordinator(Clock { currentTime }, scheduler, store, { policy })

        coordinator.onValidTrigger(snapshot())
        coordinator.onExactAlarmFired(snapshot())
        coordinator.onStopRequested(snapshot().alarmToken)

        // Cooldown until 120,000ms
        val cooldown = store.read() as AlarmState.Cooldown
        assertEquals(120_000L, cooldown.untilEpochMs)

        currentTime = 60_000L
        assertEquals(AlarmOutcome.SUPPRESSED_COOLDOWN, coordinator.onValidTrigger(snapshot().copy(alarmToken = AlarmToken("mid"))))

        currentTime = 120_000L
        assertEquals(AlarmOutcome.SCHEDULED, coordinator.onValidTrigger(snapshot().copy(alarmToken = AlarmToken("after"))))
    }

    @Test fun resetCooldownClearsActiveCooldownImmediately() = runTest {
        var currentTime = 1_000L
        val store = InMemoryAlarmStateStore()
        val scheduler = object : AlarmScheduler {
            override fun canScheduleExactAlarms() = true
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.Scheduled
            override fun cancel(token: AlarmToken) = Unit
        }
        val policy = AlarmPolicy(delayMs = 1_000, cooldownMs = 600_000) // 10 minutes
        val coordinator = AlarmCoordinator(Clock { currentTime }, scheduler, store, { policy })

        // Ring and stop -> enters 10-minute cooldown
        coordinator.onValidTrigger(snapshot())
        coordinator.onExactAlarmFired(snapshot())
        coordinator.onStopRequested(snapshot().alarmToken)
        assertTrue(store.read() is AlarmState.Cooldown)

        // Mid-cooldown trigger is suppressed
        currentTime = 10_000L
        val suppressedOutcome = coordinator.onValidTrigger(snapshot().copy(alarmToken = AlarmToken("t2")))
        assertEquals(AlarmOutcome.SUPPRESSED_COOLDOWN, suppressedOutcome)

        // Reset cooldown (e.g. user updated cooldown setting)
        coordinator.resetCooldown()
        assertEquals(AlarmState.Idle, store.read())
        assertEquals(AlarmState.Idle, coordinator.state.value)

        // Immediately after reset, new trigger is accepted and scheduled!
        val scheduledOutcome = coordinator.onValidTrigger(snapshot().copy(alarmToken = AlarmToken("t3")))
        assertEquals(AlarmOutcome.SCHEDULED, scheduledOutcome)
        assertTrue(store.read() is AlarmState.Pending)
    }
}
