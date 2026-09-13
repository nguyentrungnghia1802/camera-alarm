package com.personal.cameraalarm.schedule

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class ScheduleMode { ALWAYS_ACTIVE, CUSTOM }

data class ActiveTimeRange(
    val id: String = UUID.randomUUID().toString(),
    val startMinutes: Int, // 0..1439 minutes from midnight
    val endMinutes: Int,   // 0..1439 minutes from midnight
    val enabled: Boolean = true
) {
    fun formatStart(): String = formatMinutes(startMinutes)
    fun formatEnd(): String = formatMinutes(endMinutes)

    companion object {
        fun formatMinutes(minutes: Int): String {
            val h = (minutes / 60) % 24
            val m = minutes % 60
            return "%02d:%02d".format(h, m)
        }
    }
}

data class ScheduleConfiguration(
    val mode: ScheduleMode = ScheduleMode.ALWAYS_ACTIVE,
    val ranges: List<ActiveTimeRange> = emptyList()
)

enum class ScheduleDecision { ACTIVE, OUTSIDE_ACTIVE_HOURS }

object ActiveScheduleGate {
    fun evaluate(
        config: ScheduleConfiguration,
        eventEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScheduleDecision {
        if (config.mode == ScheduleMode.ALWAYS_ACTIVE) {
            return ScheduleDecision.ACTIVE
        }
        val enabledRanges = config.ranges.filter { it.enabled }
        if (enabledRanges.isEmpty()) {
            return ScheduleDecision.OUTSIDE_ACTIVE_HOURS
        }

        val localTime = Instant.ofEpochMilli(eventEpochMs).atZone(zoneId).toLocalTime()
        val currentMinutes = localTime.hour * 60 + localTime.minute

        for (range in enabledRanges) {
            if (isTimeInRange(currentMinutes, range.startMinutes, range.endMinutes)) {
                return ScheduleDecision.ACTIVE
            }
        }
        return ScheduleDecision.OUTSIDE_ACTIVE_HOURS
    }

    fun isTimeInRange(currentMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        // Equal boundaries: 24-hour active interval per spec (00:00 -> 00:00 is full-day active)
        if (startMinutes == endMinutes) {
            return true
        }
        // Same-day interval (e.g. 18:00 to 22:00)
        if (startMinutes < endMinutes) {
            return currentMinutes >= startMinutes && currentMinutes < endMinutes
        }
        // Overnight interval (e.g. 23:00 to 07:00)
        return currentMinutes >= startMinutes || currentMinutes < endMinutes
    }

    fun nextActiveTime(
        config: ScheduleConfiguration,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String? {
        if (config.mode == ScheduleMode.ALWAYS_ACTIVE) return null
        val enabledRanges = config.ranges.filter { it.enabled }
        if (enabledRanges.isEmpty()) return null

        val localTime = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalTime()
        val currentMinutes = localTime.hour * 60 + localTime.minute

        val nextRange = enabledRanges.minByOrNull { range ->
            val diff = range.startMinutes - currentMinutes
            if (diff > 0) diff else diff + 1440
        }
        return nextRange?.formatStart()
    }
}
