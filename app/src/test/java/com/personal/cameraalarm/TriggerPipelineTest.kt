package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TriggerPipelineTest {
    private val rule = TriggerRule("r", "person", true, "camera.app", MatchMode.CONTAINS_ANY, listOf("human"), 0, 0)
    private fun incoming(key: String, pkg: String = "camera.app") = IncomingNotification(key, pkg, 1, null, 1000, "Human detected", null, null, emptyList(), null)
    @Test fun rejectsWrongPackageDuplicateAndSubmitsMatchingRuleOnce() = runTest {
        val submitted = mutableListOf<TriggerSnapshot>()
        val history = mutableListOf<TriggerDecision>()
        val pipeline = TriggerPipeline(Clock { 1000 }, { TriggerConfiguration(true, "camera.app", listOf(rule)) }, TtlDuplicateGuard(), ValidTriggerSink {
            submitted += it; AlarmOutcome.SCHEDULED
        }, TriggerHistory { _, decision, _ -> history += decision })
        pipeline.process(incoming("other", "different"))
        pipeline.process(incoming("k"))
        pipeline.process(incoming("k"))
        assertEquals(1, submitted.size)
        assertEquals("r", submitted.single().ruleId)
        assertEquals(listOf(TriggerDecision.IGNORED_WRONG_PACKAGE, TriggerDecision.SCHEDULED, TriggerDecision.IGNORED_DUPLICATE), history)
    }
    @Test fun monitoringOffAndNoMatchNeverSubmit() = runTest {
        var count = 0
        val sink = ValidTriggerSink { count++; AlarmOutcome.SCHEDULED }
        val decisions = mutableListOf<TriggerDecision>()
        val history = TriggerHistory { _, decision, _ -> decisions += decision }
        TriggerPipeline(Clock { 0 }, { TriggerConfiguration(false, "camera.app", listOf(rule)) }, TtlDuplicateGuard(), sink, history).process(incoming("a"))
        TriggerPipeline(Clock { 0 }, { TriggerConfiguration(true, "camera.app", listOf(rule.copy(keywords = listOf("missing")))) }, TtlDuplicateGuard(), sink, history).process(incoming("b"))
        assertEquals(0, count)
        assertEquals(listOf(TriggerDecision.IGNORED_MONITORING_OFF, TriggerDecision.IGNORED_NO_RULE_MATCH), decisions)
    }

    @Test fun historyFailureDoesNotBreakScheduledAlarmPath() = runTest {
        var scheduled = 0
        var reported: Throwable? = null
        val pipeline = TriggerPipeline(
            Clock { 1_000 },
            TriggerConfigurationSource { TriggerConfiguration(true, "camera.app", listOf(rule)) },
            TtlDuplicateGuard(),
            ValidTriggerSink { scheduled++; AlarmOutcome.SCHEDULED },
            TriggerHistory { _, _, _ -> error("database unavailable") },
            historyFailure = { reported = it }
        )

        assertEquals(TriggerDecision.SCHEDULED, pipeline.process(incoming("history-failure")))
        assertEquals(1, scheduled)
        assertEquals("database unavailable", reported?.message)
    }
}
