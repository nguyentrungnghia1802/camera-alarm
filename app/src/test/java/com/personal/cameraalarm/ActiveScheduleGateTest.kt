package com.personal.cameraalarm

import com.personal.cameraalarm.schedule.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ActiveScheduleGateTest {
    private val zoneUtc = ZoneId.of("UTC")

    private fun epochAt(hour: Int, minute: Int, second: Int = 0, zone: ZoneId = zoneUtc): Long {
        val dt = ZonedDateTime.of(LocalDate.of(2026, 9, 13), LocalTime.of(hour, minute, second), zone)
        return dt.toInstant().toEpochMilli()
    }

    // --- Same-day tests: 18:00 (1080) to 22:00 (1320) ---
    private val sameDayRange = ActiveTimeRange("same-day", 18 * 60, 22 * 60, true)
    private val sameDayConfig = ScheduleConfiguration(ScheduleMode.CUSTOM, listOf(sameDayRange))

    @Test
    fun sameDayAtStartIsActive() {
        // 18:00:00 => active (inclusive)
        val decision = ActiveScheduleGate.evaluate(sameDayConfig, epochAt(18, 0, 0), zoneUtc)
        assertEquals(ScheduleDecision.ACTIVE, decision)
    }

    @Test
    fun sameDayAtLastMinuteIsActive() {
        // 21:59:59 => active
        val decision = ActiveScheduleGate.evaluate(sameDayConfig, epochAt(21, 59, 59), zoneUtc)
        assertEquals(ScheduleDecision.ACTIVE, decision)
    }

    @Test
    fun sameDayAtEndIsInactive() {
        // 22:00:00 => inactive (exclusive)
        val decision = ActiveScheduleGate.evaluate(sameDayConfig, epochAt(22, 0, 0), zoneUtc)
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, decision)
    }

    @Test
    fun sameDayBeforeStartIsInactive() {
        // 17:59:59 => inactive
        val decision = ActiveScheduleGate.evaluate(sameDayConfig, epochAt(17, 59, 59), zoneUtc)
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, decision)
    }

    // --- Overnight tests: 23:00 (1380) to 07:00 (420) ---
    private val overnightRange = ActiveTimeRange("overnight", 23 * 60, 7 * 60, true)
    private val overnightConfig = ScheduleConfiguration(ScheduleMode.CUSTOM, listOf(overnightRange))

    @Test
    fun overnightAtStartIsActive() {
        // 23:00:00 => active
        val decision = ActiveScheduleGate.evaluate(overnightConfig, epochAt(23, 0, 0), zoneUtc)
        assertEquals(ScheduleDecision.ACTIVE, decision)
    }

    @Test
    fun overnightAtLateEveningIsActive() {
        // 23:59:59 => active
        val decision = ActiveScheduleGate.evaluate(overnightConfig, epochAt(23, 59, 59), zoneUtc)
        assertEquals(ScheduleDecision.ACTIVE, decision)
    }

    @Test
    fun overnightAtMidnightIsActive() {
        // 00:00:00 => active
        val decision = ActiveScheduleGate.evaluate(overnightConfig, epochAt(0, 0, 0), zoneUtc)
        assertEquals(ScheduleDecision.ACTIVE, decision)
    }

    @Test
    fun overnightAtMorningBoundaryIsActive() {
        // 06:59:59 => active
        val decision = ActiveScheduleGate.evaluate(overnightConfig, epochAt(6, 59, 59), zoneUtc)
        assertEquals(ScheduleDecision.ACTIVE, decision)
    }

    @Test
    fun overnightAtEndIsInactive() {
        // 07:00:00 => inactive (exclusive)
        val decision = ActiveScheduleGate.evaluate(overnightConfig, epochAt(7, 0, 0), zoneUtc)
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, decision)
    }

    @Test
    fun overnightMiddayIsInactive() {
        // 12:00:00 => inactive
        val decision = ActiveScheduleGate.evaluate(overnightConfig, epochAt(12, 0, 0), zoneUtc)
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, decision)
    }

    // --- Multiple intervals ---
    private val multiConfig = ScheduleConfiguration(
        ScheduleMode.CUSTOM,
        listOf(
            ActiveTimeRange("r1", 6 * 60, 8 * 60, true),     // 06:00-08:00
            ActiveTimeRange("r2", 12 * 60, 13 * 60, true),   // 12:00-13:00
            ActiveTimeRange("r3", 23 * 60, 7 * 60, true)     // 23:00-07:00
        )
    )

    @Test
    fun multiIntervalMatchesFirst() {
        // 07:30 => matches r1
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(multiConfig, epochAt(7, 30), zoneUtc))
    }

    @Test
    fun multiIntervalMatchesMiddle() {
        // 12:30 => matches r2
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(multiConfig, epochAt(12, 30), zoneUtc))
    }

    @Test
    fun multiIntervalMatchesOvernight() {
        // 02:00 => matches r3
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(multiConfig, epochAt(2, 0), zoneUtc))
    }

    @Test
    fun multiIntervalMatchesNone() {
        // 15:00 => matches none
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, ActiveScheduleGate.evaluate(multiConfig, epochAt(15, 0), zoneUtc))
    }

    // --- Disabled intervals ---
    @Test
    fun disabledIntervalDoesNotActivate() {
        val config = ScheduleConfiguration(
            ScheduleMode.CUSTOM,
            listOf(ActiveTimeRange("disabled", 12 * 60, 14 * 60, enabled = false))
        )
        // 12:30 would match if enabled
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, ActiveScheduleGate.evaluate(config, epochAt(12, 30), zoneUtc))
    }

    // --- Empty custom schedule ---
    @Test
    fun emptyCustomScheduleIsAlwaysInactive() {
        val config = ScheduleConfiguration(ScheduleMode.CUSTOM, emptyList())
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, ActiveScheduleGate.evaluate(config, epochAt(12, 0), zoneUtc))
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, ActiveScheduleGate.evaluate(config, epochAt(0, 0), zoneUtc))
    }

    // --- Always active ---
    @Test
    fun alwaysActiveIgnoresCustomRangesAndActivatesAnyTime() {
        val config = ScheduleConfiguration(ScheduleMode.ALWAYS_ACTIVE, listOf(sameDayRange))
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochAt(3, 0), zoneUtc))
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochAt(12, 0), zoneUtc))
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochAt(23, 59), zoneUtc))
    }

    // --- Equal boundaries: 24h active ---
    @Test
    fun equalBoundariesIsFullDayActive() {
        val fullDay = ActiveTimeRange("24h", 0, 0, true)
        val config = ScheduleConfiguration(ScheduleMode.CUSTOM, listOf(fullDay))
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochAt(0, 0), zoneUtc))
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochAt(12, 34), zoneUtc))
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochAt(23, 59, 59), zoneUtc))
    }

    // --- Timezone behavior ---
    @Test
    fun scheduleEvaluatesAgainstCurrentTimezone() {
        // Range 23:00 to 07:00
        val config = ScheduleConfiguration(ScheduleMode.CUSTOM, listOf(overnightRange))
        // Event at 00:00 UTC
        val epochUtcMidnight = epochAt(0, 0, 0, zoneUtc)

        // In UTC, 00:00 is active (inside 23:00-07:00)
        assertEquals(ScheduleDecision.ACTIVE, ActiveScheduleGate.evaluate(config, epochUtcMidnight, zoneUtc))

        // In UTC+10, 00:00 UTC is 10:00 AM local time -> outside 23:00-07:00
        val zoneSydney = ZoneId.of("UTC+10")
        assertEquals(ScheduleDecision.OUTSIDE_ACTIVE_HOURS, ActiveScheduleGate.evaluate(config, epochUtcMidnight, zoneSydney))
    }

    // --- Serialization test ---
    @Test
    fun serializerRoundTripPreservesLocalTimeRanges() {
        val original = listOf(
            ActiveTimeRange("id1", 1380, 420, true),
            ActiveTimeRange("id2", 720, 780, false)
        )
        val serialized = ScheduleSerializer.serialize(original)
        val deserialized = ScheduleSerializer.deserialize(serialized)

        assertEquals(2, deserialized.size)
        assertEquals("id1", deserialized[0].id)
        assertEquals(1380, deserialized[0].startMinutes)
        assertEquals(420, deserialized[0].endMinutes)
        assertTrue(deserialized[0].enabled)

        assertEquals("id2", deserialized[1].id)
        assertEquals(720, deserialized[1].startMinutes)
        assertEquals(780, deserialized[1].endMinutes)
        assertFalse(deserialized[1].enabled)
    }
}
