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

    @Test fun outsideHoursEventIsSuppressedAndDoesNotMarkDuplicate() = runTest {
        // Schedule: 23:00 to 07:00 (1380 to 420)
        val schedule = com.personal.cameraalarm.schedule.ScheduleConfiguration(
            com.personal.cameraalarm.schedule.ScheduleMode.CUSTOM,
            listOf(com.personal.cameraalarm.schedule.ActiveTimeRange("night", 23 * 60, 7 * 60, true))
        )
        val history = mutableListOf<TriggerDecision>()
        var scheduled = 0
        val duplicates = TtlDuplicateGuard()

        // 12:00:00 (midday) epoch time in UTC
        val middayEpoch = java.time.ZonedDateTime.of(
            java.time.LocalDate.of(2026, 9, 13),
            java.time.LocalTime.of(12, 0, 0),
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()

        // 02:00:00 (overnight) epoch time
        val nightEpoch = java.time.ZonedDateTime.of(
            java.time.LocalDate.of(2026, 9, 13),
            java.time.LocalTime.of(2, 0, 0),
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()

        val pipeline = TriggerPipeline(
            Clock { middayEpoch },
            TriggerConfigurationSource { TriggerConfiguration(true, "camera.app", listOf(rule), schedule) },
            duplicates,
            ValidTriggerSink { scheduled++; AlarmOutcome.SCHEDULED },
            TriggerHistory { _, decision, _ -> history += decision }
        )

        // Midday notification -> suppressed outside active hours
        val suppressedDecision = pipeline.process(
            IncomingNotification("notif-1", "camera.app", 1, null, middayEpoch, "Human detected", null, null, emptyList(), null)
        )
        assertEquals(TriggerDecision.SUPPRESSED_OUTSIDE_ACTIVE_HOURS, suppressedDecision)
        assertEquals(0, scheduled)
        // Key should NOT be marked seen in duplicates
        assertFalse(duplicates.isDuplicate("notif-1", middayEpoch))

        // Later event at night with same key -> should schedule successfully!
        val nightPipeline = TriggerPipeline(
            Clock { nightEpoch },
            TriggerConfigurationSource { TriggerConfiguration(true, "camera.app", listOf(rule), schedule) },
            duplicates,
            ValidTriggerSink { scheduled++; AlarmOutcome.SCHEDULED },
            TriggerHistory { _, decision, _ -> history += decision }
        )
        val scheduledDecision = nightPipeline.process(
            IncomingNotification("notif-1", "camera.app", 1, null, nightEpoch, "Human detected", null, null, emptyList(), null)
        )
        assertEquals(TriggerDecision.SCHEDULED, scheduledDecision)
        assertEquals(1, scheduled)
    }

    @Test fun boundaryAcceptedAt065959SchedulesEvenIfDelayedPast0700() = runTest {
        // Schedule: 23:00 to 07:00
        val schedule = com.personal.cameraalarm.schedule.ScheduleConfiguration(
            com.personal.cameraalarm.schedule.ScheduleMode.CUSTOM,
            listOf(com.personal.cameraalarm.schedule.ActiveTimeRange("night", 23 * 60, 7 * 60, true))
        )
        val boundaryEpoch = java.time.ZonedDateTime.of(
            java.time.LocalDate.of(2026, 9, 13),
            java.time.LocalTime.of(6, 59, 59),
            java.time.ZoneId.systemDefault()
        ).toInstant().toEpochMilli()

        var scheduledSnapshot: TriggerSnapshot? = null
        val pipeline = TriggerPipeline(
            Clock { boundaryEpoch },
            TriggerConfigurationSource { TriggerConfiguration(true, "camera.app", listOf(rule), schedule) },
            TtlDuplicateGuard(),
            ValidTriggerSink { snapshot -> scheduledSnapshot = snapshot; AlarmOutcome.SCHEDULED },
            TriggerHistory { _, _, _ -> }
        )

        val decision = pipeline.process(
            IncomingNotification("boundary-1", "camera.app", 1, null, boundaryEpoch, "Human detected", null, null, emptyList(), null)
        )
        assertEquals(TriggerDecision.SCHEDULED, decision)
        assertNotNull(scheduledSnapshot)
        // Event was scheduled based on event time 06:59:59
        assertEquals(boundaryEpoch, scheduledSnapshot?.receivedAtEpochMs)
    }
}
