package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingDeliveryBoundaryTest {
    private val old = AlarmToken("old")
    private val retry = AlarmToken("retry")
    private fun trigger(token: AlarmToken) = TriggerSnapshot(token, "camera", "key", "rule", null, null, 0)
    private class Scheduler : AlarmScheduler {
        val scheduled = mutableListOf<AlarmToken>()
        override fun canScheduleExactAlarms() = true
        override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult {
            scheduled += token
            return ScheduleResult.Scheduled
        }
        override fun cancel(token: AlarmToken) = Unit
    }
    private fun owner(clock: Clock, store: AlarmStateStore, scheduler: Scheduler = Scheduler()) =
        AlarmCoordinator(clock, scheduler, store, { AlarmPolicy(delayMs = 1_000, cooldownMs = 0) }, newToken = { retry })

    @Test fun callbackWinningBeforeAtOrAfterGraceClaimsExactlyOnce() = runTest {
        for (offset in listOf(-1L, 0L, 1L)) {
            var now = 0L
            val store = InMemoryAlarmStateStore()
            val c = owner(Clock { now }, store)
            c.onValidTrigger(trigger(old))
            now = 1_000 + PendingRecoveryPolicy.GRACE_MS + offset
            var starts = 0
            assertTrue("offset=$offset", c.claimAlarm(old) { starts++ })
            assertFalse(c.recoverPending(old))
            assertFalse(c.claimAlarm(old) { starts++ })
            assertEquals(1, starts)
            assertEquals(old, (c.state.value as AlarmState.Ringing).trigger.alarmToken)
            assertEquals(store.read(), c.state.value)
        }
    }

    @Test fun watchdogBeforeGraceKeepsOriginalDeadlineAndBurstSuppression() = runTest {
        var now = 0L
        val scheduler = Scheduler()
        val c = owner(Clock { now }, InMemoryAlarmStateStore(), scheduler)
        c.onValidTrigger(trigger(old))
        val pending = c.state.value
        now = 1_000 + PendingRecoveryPolicy.GRACE_MS - 1
        assertTrue(c.recoverPending(old))
        assertEquals(AlarmOutcome.SUPPRESSED_PENDING, c.onValidTrigger(trigger(AlarmToken("burst"))))
        assertEquals(pending, c.state.value)
        assertEquals(listOf(old), scheduler.scheduled)
        assertTrue(c.claimAlarm(old) {})
    }

    @Test fun watchdogWinningAtOrAfterGraceTransfersExclusiveOwnership() = runTest {
        for (offset in listOf(0L, 1L)) {
            var now = 0L
            val store = InMemoryAlarmStateStore()
            val c = owner(Clock { now }, store)
            c.onValidTrigger(trigger(old))
            now = 1_000 + PendingRecoveryPolicy.GRACE_MS + offset
            assertFalse(c.recoverPending(old))
            val replacement = c.state.value
            assertEquals(retry, (replacement as AlarmState.Pending).trigger.alarmToken)
            assertFalse(c.claimAlarm(old) { fail("Retired callback claimed retry") })
            c.onStopRequested(old)
            assertEquals(replacement, c.state.value)
            assertEquals(AlarmOutcome.SUPPRESSED_PENDING, c.onValidTrigger(trigger(AlarmToken("burst"))))
            var starts = 0
            assertTrue(c.claimAlarm(retry) { starts++ })
            assertFalse(c.claimAlarm(old) { starts++ })
            assertFalse(c.claimAlarm(retry) { starts++ })
            assertEquals(1, starts)
            assertEquals(store.read(), c.state.value)
        }
    }

    @Test fun callbackQueuedDuringRetirementCannotClaimReplacement() = runTest {
        var now = 0L
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delegate = InMemoryAlarmStateStore()
        val store = object : AlarmStateStore {
            override suspend fun read() = delegate.read()
            override suspend fun write(state: AlarmState) {
                if (state == AlarmState.Idle) { entered.complete(Unit); release.await() }
                delegate.write(state)
            }
        }
        val c = owner(Clock { now }, store)
        c.onValidTrigger(trigger(old))
        now = 1_000 + PendingRecoveryPolicy.GRACE_MS
        val recovery = async { c.recoverPending(old) }
        entered.await()
        val callback = async { c.claimAlarm(old) { fail("Callback overtook retirement") } }
        runCurrent()
        assertFalse(callback.isCompleted)
        release.complete(Unit)
        assertFalse(recovery.await())
        assertFalse(callback.await())
        assertEquals(retry, (c.state.value as AlarmState.Pending).trigger.alarmToken)
        assertTrue(c.claimAlarm(retry) {})
        assertEquals(store.read(), c.state.value)
    }

    @Test fun recoveryQueuedDuringCallbackCannotInvalidateRingingOwner() = runTest {
        var now = 0L
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delegate = InMemoryAlarmStateStore()
        val store = object : AlarmStateStore {
            override suspend fun read() = delegate.read()
            override suspend fun write(state: AlarmState) {
                if (state is AlarmState.Ringing) { entered.complete(Unit); release.await() }
                delegate.write(state)
            }
        }
        val scheduler = Scheduler()
        val c = owner(Clock { now }, store, scheduler)
        c.onValidTrigger(trigger(old))
        now = 1_000 + PendingRecoveryPolicy.GRACE_MS
        var starts = 0
        val callback = async { c.claimAlarm(old) { starts++ } }
        entered.await()
        val recovery = async { c.recoverPending(old) }
        runCurrent()
        assertFalse(recovery.isCompleted)
        release.complete(Unit)
        assertTrue(callback.await())
        assertFalse(recovery.await())
        assertEquals(1, starts)
        assertEquals(listOf(old), scheduler.scheduled)
        assertEquals(old, (c.state.value as AlarmState.Ringing).trigger.alarmToken)
        assertEquals(store.read(), c.state.value)
    }
}
