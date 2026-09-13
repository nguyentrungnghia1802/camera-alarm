package com.personal.cameraalarm

import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun incoming(key: String = "k") = IncomingNotification(key, "camera.app", 1, null, 1000, "  HUMAN\n", "Detected  ", null, listOf("ở Cửa"), null)
    private fun rule(mode: MatchMode = MatchMode.CONTAINS_ANY, words: List<String> = listOf("human detected"), id: String = "one", priority: Int = 0, created: Long = 0) = TriggerRule(id, id, true, "camera.app", mode, words, priority, created)
    private fun snapshot(token: String = "t") = TriggerSnapshot(AlarmToken(token), "camera.app", "k", "one", "Human", "Detected", 1000)

    @Test fun normalizerMergesFieldsAndPreservesAccents() {
        assertEquals("human detected ở cửa", NotificationNormalizer.normalize(incoming()))
        assertEquals("", NotificationNormalizer.normalize(incoming().copy(title = null, text = null, textLines = emptyList())))
        assertEquals("phát hiện người", NotificationNormalizer.normalize(incoming().copy(title = null, text = null, bigText = " PHÁT  HIỆN\nNGƯỜI ", textLines = emptyList())))
    }
    @Test fun matcherChecksPackageModeAndPriority() {
        val text = NotificationNormalizer.normalize(incoming())
        assertNull(TriggerMatcher.match("other", text, listOf(rule())))
        assertNull(TriggerMatcher.match("camera.app", text, listOf(rule(words = emptyList()))))
        assertNull(TriggerMatcher.match("camera.app", text, listOf(rule(words = listOf("absent")))))
        assertEquals("one", TriggerMatcher.match("camera.app", text, listOf(rule(words = listOf("absent", "HUMAN  detected"))))?.id)
        assertEquals("one", TriggerMatcher.match("camera.app", text, listOf(rule(MatchMode.CONTAINS_ALL, listOf("human", "ở cửa", " HUMAN "))))?.id)
        assertNull(TriggerMatcher.match("camera.app", text, listOf(rule(MatchMode.CONTAINS_ALL, listOf("human", "missing")))))
        assertEquals("early", TriggerMatcher.match("camera.app", text, listOf(rule(id = "later", priority = 1), rule(id = "early", priority = 0)))?.id)
    }
    @Test fun dedupeTtlBoundaryAndBoundedCache() {
        val guard = TtlDuplicateGuard(ttlMs = 30_000, maxEntries = 2)
        assertFalse(guard.isDuplicate("a", 0)); guard.markSeen("a", 0)
        assertTrue(guard.isDuplicate("a", 29_999)); assertFalse(guard.isDuplicate("a", 30_000))
        guard.markSeen("a", 40_000); guard.markSeen("b", 40_001); guard.markSeen("c", 40_002)
        assertEquals(2, guard.size); assertFalse(guard.isDuplicate("a", 40_003)); assertTrue(guard.isDuplicate("b", 40_003))
        guard.prune(70_002); assertEquals(0, guard.size)
    }
    @Test fun reducerTransitionsAndStaleEvents() {
        val policy = AlarmPolicy(delayMs = 1000, cooldownMs = 10_000)
        val t = snapshot(); val other = snapshot("other")
        val pending = AlarmReducer.reduce(AlarmState.Idle, AlarmEvent.ValidTrigger(t), 100, policy)
        assertEquals(AlarmState.Pending(t, 1100), pending.nextState)
        assertEquals(1, pending.effects.filterIsInstance<AlarmEffect.ScheduleExact>().size)
        assertEquals(pending.nextState, AlarmReducer.reduce(pending.nextState, AlarmEvent.ValidTrigger(other), 101, policy).nextState)
        assertEquals(pending.nextState, AlarmReducer.reduce(pending.nextState, AlarmEvent.ExactAlarmFired(other), 1100, policy).nextState)
        val ringing = AlarmReducer.reduce(pending.nextState, AlarmEvent.ExactAlarmFired(t), 1100, policy)
        assertEquals(AlarmState.Ringing(t, 1100), ringing.nextState)
        assertEquals(ringing.nextState, AlarmReducer.reduce(ringing.nextState, AlarmEvent.ValidTrigger(other), 1200, policy).nextState)
        assertEquals(ringing.nextState, AlarmReducer.reduce(ringing.nextState, AlarmEvent.StopRequested(other.alarmToken), 1200, policy).nextState)
        val stopped = AlarmReducer.reduce(ringing.nextState, AlarmEvent.StopRequested(null), 1200, policy)
        assertEquals(AlarmState.Cooldown(11200, t.alarmToken), stopped.nextState)
        assertEquals(1, stopped.effects.filterIsInstance<AlarmEffect.StopRuntime>().size)
        assertTrue(AlarmReducer.reduce(stopped.nextState, AlarmEvent.StopRequested(null), 1201, policy).effects.isEmpty())
        assertEquals(stopped.nextState, AlarmReducer.reduce(stopped.nextState, AlarmEvent.ValidTrigger(other), 11199, policy).nextState)
        assertTrue(AlarmReducer.reduce(stopped.nextState, AlarmEvent.ValidTrigger(other), 11200, policy).nextState is AlarmState.Pending)
        assertEquals(AlarmState.Idle, AlarmReducer.reduce(pending.nextState, AlarmEvent.ScheduleFailed(t.alarmToken), 100, policy).nextState)
        assertEquals(pending.nextState, AlarmReducer.reduce(pending.nextState, AlarmEvent.ScheduleFailed(other.alarmToken), 100, policy).nextState)
        assertTrue(AlarmReducer.reduce(pending.nextState, AlarmEvent.StopRequested(t.alarmToken), 100, policy).nextState is AlarmState.Cooldown)
    }
    @Test fun concurrentTriggersOnlyScheduleOnce() = runTest {
        val scheduler = RecordingScheduler(); val clock = Clock { 1000L }
        val coordinator = AlarmCoordinator(clock, scheduler, InMemoryAlarmStateStore(), { AlarmPolicy() })
        (1..20).map { i -> async { coordinator.onValidTrigger(snapshot("$i")) } }.awaitAll()
        assertEquals(1, scheduler.scheduled.size)
        assertTrue(coordinator.state.value is AlarmState.Pending)
    }
    @Test fun lateAlarmStillRingsButDuplicateFireAfterStopIsStale() {
        val t = snapshot()
        val pending = AlarmState.Pending(t, 1100)
        val late = AlarmReducer.reduce(pending, AlarmEvent.ExactAlarmFired(t), 50_000, AlarmPolicy())
        assertEquals(AlarmState.Ringing(t, 50_000), late.nextState)
        val stopped = AlarmReducer.reduce(late.nextState, AlarmEvent.StopRequested(t.alarmToken), 50_001, AlarmPolicy())
        val duplicate = AlarmReducer.reduce(stopped.nextState, AlarmEvent.ExactAlarmFired(t), 50_002, AlarmPolicy())
        assertEquals(stopped.nextState, duplicate.nextState)
        assertTrue(duplicate.effects.none { it is AlarmEffect.StartRinging })
    }
    private class RecordingScheduler : AlarmScheduler {
        val scheduled = mutableListOf<AlarmToken>()
        override fun canScheduleExactAlarms() = true
        override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult { scheduled += token; return ScheduleResult.Scheduled }
        override fun cancel(token: AlarmToken) = Unit
    }
}
