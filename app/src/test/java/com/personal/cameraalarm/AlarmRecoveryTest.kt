package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRecoveryTest {
    private fun trigger(token: String = "a") = TriggerSnapshot(AlarmToken(token), "camera", "n-$token", "rule", null, null, 0)
    private class Scheduler : AlarmScheduler {
        var granted = true
        var result: ScheduleResult = ScheduleResult.Scheduled
        val scheduled = mutableListOf<Pair<AlarmToken, Long>>()
        val cancelled = mutableListOf<AlarmToken>()
        override fun canScheduleExactAlarms() = granted
        override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult {
            scheduled += token to triggerAtEpochMs
            return result
        }
        override fun cancel(token: AlarmToken) { cancelled += token }
    }
    private class Watchdog : PendingRecoveryScheduler {
        val armed = mutableListOf<AlarmState.Pending>()
        var enabled = true
        override fun arm(pending: AlarmState.Pending): Boolean { armed += pending; return enabled }
        override fun cancel(token: AlarmToken) = Unit
    }

    @Test fun burstKeepsDeadlineAndSingleDelivery() = runTest {
        val store = InMemoryAlarmStateStore()
        val scheduler = Scheduler()
        val c = AlarmCoordinator(Clock { testScheduler.currentTime }, scheduler, store, { AlarmPolicy() })
        c.onValidTrigger(trigger())
        val pending = c.state.value
        repeat(20) { assertEquals(AlarmOutcome.SUPPRESSED_PENDING, c.onValidTrigger(trigger("b$it"))) }
        assertEquals(pending, c.state.value)
        assertEquals(1, scheduler.scheduled.size)
        var starts = 0
        assertTrue(c.claimAlarm(AlarmToken("a")) { starts++ })
        assertFalse(c.claimAlarm(AlarmToken("a")) { starts++ })
        assertEquals(1, starts)
        assertEquals(store.read(), c.state.value)
    }

    @Test fun autonomousWatchdogRetriesOnceThenReleasesWithoutAnotherNotification() = runTest {
        val store = InMemoryAlarmStateStore()
        val scheduler = Scheduler()
        val c = AlarmCoordinator(Clock { testScheduler.currentTime }, scheduler, store, { AlarmPolicy() }, newToken = { AlarmToken("b") })
        c.watchPending(backgroundScope)
        c.onValidTrigger(trigger())
        runCurrent()
        advanceTimeBy(1_000 + PendingRecoveryPolicy.GRACE_MS - 1); runCurrent()
        assertEquals(AlarmToken("a"), (c.state.value as AlarmState.Pending).trigger.alarmToken)
        advanceTimeBy(1); runCurrent()
        val retry = c.state.value as AlarmState.Pending
        assertEquals(AlarmToken("b"), retry.trigger.alarmToken)
        assertEquals(1, retry.recoveryAttempt)
        assertFalse(c.claimAlarm(AlarmToken("a")) { fail("Retired token played") })
        advanceTimeBy(PendingRecoveryPolicy.GRACE_MS); runCurrent()
        assertEquals(AlarmState.Idle, c.state.value)
        assertEquals(store.read(), c.state.value)
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(AlarmOutcome.SCHEDULED, c.onValidTrigger(trigger("c")))
        assertFalse(c.claimAlarm(AlarmToken("b")) { fail("Late retry played") })
        assertEquals(AlarmToken("c"), (c.state.value as AlarmState.Pending).trigger.alarmToken)
    }

    @Test fun zeroDelayStillAllowsPlatformMinimumFuturityBeforeRecovery() = runTest {
        val c = AlarmCoordinator(Clock { testScheduler.currentTime }, Scheduler(), InMemoryAlarmStateStore(), { AlarmPolicy(delayMs = 0) })
        c.watchPending(backgroundScope)
        c.onValidTrigger(trigger())
        runCurrent()
        advanceTimeBy(5_100); runCurrent()
        assertEquals(0, (c.state.value as AlarmState.Pending).recoveryAttempt)
        assertTrue(c.claimAlarm(AlarmToken("a")) {})
    }
    @Test fun lateDeliveryWithinGraceWinsOnce() = runTest {
        val c = AlarmCoordinator(Clock { testScheduler.currentTime }, Scheduler(), InMemoryAlarmStateStore(), { AlarmPolicy() })
        c.onValidTrigger(trigger())
        advanceTimeBy(4_000)
        assertTrue(c.claimAlarm(AlarmToken("a")) {})
        assertFalse(c.recoverPending(AlarmToken("a")))
        assertTrue(c.state.value is AlarmState.Ringing)
    }

    @Test fun duplicateRecoveryAndOldStopCannotRetireReplacement() = runTest {
        val scheduler = Scheduler()
        val c = AlarmCoordinator(Clock { testScheduler.currentTime }, scheduler, InMemoryAlarmStateStore(),
            { AlarmPolicy() }, newToken = { AlarmToken("replacement") })
        c.onValidTrigger(trigger())
        advanceTimeBy(1_000 + PendingRecoveryPolicy.GRACE_MS)
        coroutineScope { repeat(10) { launch { c.recoverPending(AlarmToken("a")) } } }
        c.onStopRequested(AlarmToken("a"))
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(AlarmToken("replacement"), (c.state.value as AlarmState.Pending).trigger.alarmToken)
    }

    @Test fun receiverDispatchFailureReleasesOwnership() = runTest {
        val store = InMemoryAlarmStateStore()
        val c = AlarmCoordinator(Clock { 0 }, Scheduler(), store, { AlarmPolicy() })
        c.onValidTrigger(trigger())
        try { c.claimAlarm(AlarmToken("a")) { error("FGS denied") }; fail() }
        catch (_: IllegalStateException) { }
        assertEquals(AlarmState.Idle, c.state.value)
        assertEquals(store.read(), c.state.value)
        assertFalse(c.withRingingOwnership(AlarmToken("a")) { fail() })
    }

    @Test fun crashAfterPersistBeforeScheduleRearmsOnHydrateAndRepeatedBootDoesNotResetIt() = runTest {
        val store = InMemoryAlarmStateStore()
        store.write(AlarmState.Pending(trigger(), 1_000, 1_000))
        val scheduler = Scheduler()
        val watchdog = Watchdog()
        val c = AlarmCoordinator(Clock { 0 }, scheduler, store, { AlarmPolicy() }, recoveryScheduler = watchdog)
        repeat(4) { c.reconcile() }
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(1, watchdog.armed.size)
        assertTrue(c.initialized.value)
        assertEquals(store.read(), c.state.value)
        assertEquals(AlarmToken("a"), (c.state.value as AlarmState.Pending).trigger.alarmToken)
    }

    @Test fun recoveryBudgetSurvivesProcessRestart() = runTest {
        val store = InMemoryAlarmStateStore()
        store.write(AlarmState.Pending(trigger("retry"), 0, 0, recoveryAttempt = 1))
        val scheduler = Scheduler()
        val c = AlarmCoordinator(Clock { PendingRecoveryPolicy.GRACE_MS + 1 }, scheduler, store, { AlarmPolicy() })
        c.reconcile()
        assertEquals(AlarmState.Idle, c.state.value)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun revokeRegrantCannotResurrectRetiredToken() = runTest {
        val scheduler = Scheduler()
        val store = InMemoryAlarmStateStore()
        val c = AlarmCoordinator(Clock { 0 }, scheduler, store, { AlarmPolicy() })
        c.onValidTrigger(trigger())
        scheduler.granted = false
        c.reconcile()
        assertEquals(AlarmState.Idle, c.state.value)
        scheduler.granted = true
        c.onValidTrigger(trigger("new"))
        assertFalse(c.claimAlarm(AlarmToken("a")) { fail() })
        assertEquals(store.read(), c.state.value)
    }

    @Test fun missingWatchdogAndScheduleFailureNeverLeavePending() = runTest {
        val store = InMemoryAlarmStateStore()
        val scheduler = Scheduler()
        val watchdog = Watchdog().apply { enabled = false }
        val c = AlarmCoordinator(Clock { 0 }, scheduler, store, { AlarmPolicy() }, recoveryScheduler = watchdog)
        assertEquals(AlarmOutcome.SCHEDULE_FAILED, c.onValidTrigger(trigger()))
        assertTrue(scheduler.scheduled.isEmpty())
        watchdog.enabled = true
        scheduler.result = ScheduleResult.Failed("denied")
        assertEquals(AlarmOutcome.SCHEDULE_FAILED, c.onValidTrigger(trigger()))
        assertEquals(AlarmState.Idle, store.read())
        assertEquals(store.read(), c.state.value)
    }

    @Test fun bootAndReceiverWaitForTriggerTransactionAndDoNotEraseNewState() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delegate = InMemoryAlarmStateStore()
        val store = object : AlarmStateStore {
            override suspend fun read() = delegate.read()
            override suspend fun write(state: AlarmState) {
                if (state is AlarmState.Pending) { entered.complete(Unit); release.await() }
                delegate.write(state)
            }
        }
        val c = AlarmCoordinator(Clock { 0 }, Scheduler(), store, { AlarmPolicy() })
        val triggerJob = launch { c.onValidTrigger(trigger()) }
        entered.await()
        val boot = async { c.reconcile() }
        var starts = 0
        val fired = async { c.claimAlarm(AlarmToken("a")) { starts++ } }
        runCurrent()
        assertFalse(boot.isCompleted)
        assertFalse(fired.isCompleted)
        release.complete(Unit)
        triggerJob.join(); boot.await(); assertTrue(fired.await())
        assertEquals(1, starts)
        assertTrue(c.state.value is AlarmState.Ringing)
        repeat(3) { c.reconcile() }
        assertEquals(store.read(), c.state.value)
        assertTrue(c.state.value is AlarmState.Ringing)
    }

    @Test fun stopBetweenReceiverAndServicePreventsAudioAndPreservesNewToken() = runTest {
        var now = 0L
        val c = AlarmCoordinator(Clock { now }, Scheduler(), InMemoryAlarmStateStore(), { AlarmPolicy(cooldownMs = 0) })
        c.onValidTrigger(trigger())
        c.claimAlarm(AlarmToken("a")) {}
        c.onStopRequested(AlarmToken("a"))
        now++
        c.onValidTrigger(trigger("new"))
        assertFalse(c.withRingingOwnership(AlarmToken("a")) { fail("Stale service started audio") })
        c.onStopRequested(AlarmToken("a"))
        assertEquals(AlarmToken("new"), (c.state.value as AlarmState.Pending).trigger.alarmToken)
    }

    @Test fun monotonicDeadlineDoesNotExtendWhenWallClockMovesBack() = runTest {
        var epoch = 100_000L
        var elapsed = 100L
        val c = AlarmCoordinator(Clock { epoch }, Scheduler(), InMemoryAlarmStateStore(), { AlarmPolicy() }, elapsedMs = { elapsed })
        c.onValidTrigger(trigger())
        epoch = 0
        elapsed += 1_000 + PendingRecoveryPolicy.GRACE_MS
        c.recoverPending(AlarmToken("a"))
        assertEquals(1, (c.state.value as AlarmState.Pending).recoveryAttempt)
    }
}
