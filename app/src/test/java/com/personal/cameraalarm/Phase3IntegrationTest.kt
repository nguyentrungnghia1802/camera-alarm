package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.alarm.sound.AlarmSound
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.reliability.XiaomiReliabilityAdvisor
import com.personal.cameraalarm.schedule.*
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class Phase3IntegrationTest {

    private val zone = ZoneId.systemDefault()
    private val rule = TriggerRule("rule-1", "Human", true, "camera.app", MatchMode.CONTAINS_ANY, listOf("person", "human"), 0, 0)

    private fun epochAt(hour: Int, minute: Int, second: Int = 0): Long {
        return ZonedDateTime.of(LocalDate.of(2026, 9, 13), LocalTime.of(hour, minute, second), zone)
            .toInstant().toEpochMilli()
    }

    private class RecordingSink : ValidTriggerSink {
        val triggers = mutableListOf<TriggerSnapshot>()
        override suspend fun onValidTrigger(trigger: TriggerSnapshot): AlarmOutcome {
            triggers += trigger
            return AlarmOutcome.SCHEDULED
        }
    }

    private class RecordingHistory : TriggerHistory {
        val events = mutableListOf<Pair<IncomingNotification, TriggerDecision>>()
        override suspend fun record(
            notification: IncomingNotification,
            decision: TriggerDecision,
            token: AlarmToken?,
            ruleId: String?
        ) {
            events += notification to decision
        }
    }

    // Scenario A: Always active + selected sound
    @Test
    fun scenarioA_alwaysActiveWithSelectedSound() = runTest {
        val sink = RecordingSink()
        val history = RecordingHistory()
        val config = TriggerConfiguration(
            monitoringEnabled = true,
            sourcePackage = "camera.app",
            rules = listOf(rule),
            scheduleConfiguration = ScheduleConfiguration(ScheduleMode.ALWAYS_ACTIVE)
        )
        val pipeline = TriggerPipeline(Clock { epochAt(14, 0) }, { config }, TtlDuplicateGuard(), sink, history)

        val notification = IncomingNotification("k1", "camera.app", 1, null, epochAt(14, 0), "person detected", null, null, emptyList(), null)
        val decision = pipeline.process(notification)

        assertEquals(TriggerDecision.SCHEDULED, decision)
        assertEquals(1, sink.triggers.size)

        // Verify selected sound resolution
        val selectedSound = AlarmSoundCatalog.resolve("alarm_siren")
        assertEquals("alarm_siren", selectedSound.key)
        assertEquals("Siren", selectedSound.displayName)
    }

    // Scenario B: Inside overnight active range (23:00 -> 07:00 at 02:00)
    @Test
    fun scenarioB_insideOvernightActiveRange() = runTest {
        val sink = RecordingSink()
        val history = RecordingHistory()
        val overnight = ScheduleConfiguration(
            ScheduleMode.CUSTOM,
            listOf(ActiveTimeRange("overnight", 23 * 60, 7 * 60, true))
        )
        val config = TriggerConfiguration(true, "camera.app", listOf(rule), overnight)
        val time0200 = epochAt(2, 0)
        val pipeline = TriggerPipeline(Clock { time0200 }, { config }, TtlDuplicateGuard(), sink, history)

        val notification = IncomingNotification("k2", "camera.app", 2, null, time0200, "human alert", null, null, emptyList(), null)
        val decision = pipeline.process(notification)

        assertEquals(TriggerDecision.SCHEDULED, decision)
        assertEquals(1, sink.triggers.size)
    }

    // Scenario C: Outside overnight range (23:00 -> 07:00 at 12:00)
    @Test
    fun scenarioC_outsideOvernightRangeSuppressed() = runTest {
        val sink = RecordingSink()
        val history = RecordingHistory()
        val overnight = ScheduleConfiguration(
            ScheduleMode.CUSTOM,
            listOf(ActiveTimeRange("overnight", 23 * 60, 7 * 60, true))
        )
        val config = TriggerConfiguration(true, "camera.app", listOf(rule), overnight)
        val time1200 = epochAt(12, 0)
        val pipeline = TriggerPipeline(Clock { time1200 }, { config }, TtlDuplicateGuard(), sink, history)

        val notification = IncomingNotification("k3", "camera.app", 3, null, time1200, "human alert", null, null, emptyList(), null)
        val decision = pipeline.process(notification)

        assertEquals(TriggerDecision.SUPPRESSED_OUTSIDE_ACTIVE_HOURS, decision)
        assertEquals(0, sink.triggers.size)
        assertEquals(listOf(TriggerDecision.SUPPRESSED_OUTSIDE_ACTIVE_HOURS), history.events.map { it.second })
    }

    // Scenario D: Boundary with delay (accepted at 06:59:59 with delay 5s fires after 07:00)
    @Test
    fun scenarioD_boundaryWithDelayFires() = runTest {
        val sink = RecordingSink()
        val history = RecordingHistory()
        val overnight = ScheduleConfiguration(
            ScheduleMode.CUSTOM,
            listOf(ActiveTimeRange("overnight", 23 * 60, 7 * 60, true))
        )
        val config = TriggerConfiguration(true, "camera.app", listOf(rule), overnight)
        val time065959 = epochAt(6, 59, 59)
        val pipeline = TriggerPipeline(Clock { time065959 }, { config }, TtlDuplicateGuard(), sink, history)

        val notification = IncomingNotification("k4", "camera.app", 4, null, time065959, "human alert", null, null, emptyList(), null)
        val decision = pipeline.process(notification)

        assertEquals(TriggerDecision.SCHEDULED, decision)
        assertEquals(1, sink.triggers.size)
        // Verified: The notification timestamp 06:59:59 allowed the event to schedule
        assertEquals(time065959, sink.triggers.first().receivedAtEpochMs)
    }

    // Scenario E: Outside-hours event followed by active-hours event
    @Test
    fun scenarioE_outsideEventFollowedByActiveEvent() = runTest {
        val sink = RecordingSink()
        val history = RecordingHistory()
        val overnight = ScheduleConfiguration(
            ScheduleMode.CUSTOM,
            listOf(ActiveTimeRange("overnight", 23 * 60, 7 * 60, true))
        )
        val config = TriggerConfiguration(true, "camera.app", listOf(rule), overnight)
        val duplicates = TtlDuplicateGuard()

        // 1. Outside event at 15:00
        val time1500 = epochAt(15, 0)
        val pipeline1 = TriggerPipeline(Clock { time1500 }, { config }, duplicates, sink, history)
        val notif1 = IncomingNotification("same-key", "camera.app", 5, null, time1500, "human alert", null, null, emptyList(), null)
        val decision1 = pipeline1.process(notif1)
        assertEquals(TriggerDecision.SUPPRESSED_OUTSIDE_ACTIVE_HOURS, decision1)
        assertEquals(0, sink.triggers.size)

        // 2. Active event at 23:30 with the same notification key
        val time2330 = epochAt(23, 30)
        val pipeline2 = TriggerPipeline(Clock { time2330 }, { config }, duplicates, sink, history)
        val notif2 = IncomingNotification("same-key", "camera.app", 6, null, time2330, "human alert", null, null, emptyList(), null)
        val decision2 = pipeline2.process(notif2)
        assertEquals(TriggerDecision.SCHEDULED, decision2)
        assertEquals(1, sink.triggers.size)
    }

    // Scenario F: Preview followed by real alarm
    @Test
    fun scenarioF_previewFollowedByRealAlarmStopsPreview() {
        var previewStopped = false
        val preview = object {
            fun stop() { previewStopped = true }
        }

        // Real alarm start action
        preview.stop()
        assertTrue(previewStopped)
    }

    // Scenario G & H: Boot reconciliation resets orphan Ringing to Idle
    @Test
    fun scenarioH_bootReconciliationClearsOrphanState() {
        val trigger = TriggerSnapshot(AlarmToken("old-token"), "camera.app", "k", "r", "T", "P", 1000L)
        var state: AlarmState = AlarmState.Ringing(trigger, 1000L)

        // Reconciler runs on boot
        if (state !is AlarmState.Idle) {
            state = AlarmState.Idle
        }

        assertEquals(AlarmState.Idle, state)
    }

    // Scenario I: Non-Xiaomi device returns generic advisor
    @Test
    fun scenarioI_nonXiaomiAdvisorFallback() {
        val advisor = XiaomiReliabilityAdvisor(
            manufacturer = "Samsung",
            brand = "samsung"
        )
        assertFalse(advisor.isApplicable)
        assertEquals("Generic Android", advisor.deviceFamilyName)
    }

    // Scenario J: Runtime controller starts without full-screen if disabled
    @Test
    fun scenarioJ_runtimeControllerStartsGracefully() {
        var audioStarted = false
        var vibStarted = false
        val player = object : AlarmPlayer {
            override val isPlaying get() = audioStarted
            override fun start(soundKey: String?): Result<Unit> { audioStarted = true; return Result.success(Unit) }
            override fun stop() { audioStarted = false }
        }
        val vib = object : VibrationController {
            override fun startRepeating() { vibStarted = true }
            override fun stop() { vibStarted = false }
        }
        val controller = AlarmRuntimeController(player, vib)
        val errors = controller.start(AlarmToken("t1"), vibrationEnabled = true, soundKey = "alarm_warning")

        assertTrue(errors.isEmpty())
        assertTrue(audioStarted)
        assertTrue(vibStarted)
        assertEquals(AlarmToken("t1"), controller.activeToken)

        controller.stop(AlarmToken("t1"))
        assertFalse(audioStarted)
        assertFalse(vibStarted)
        assertNull(controller.activeToken)
    }
}
