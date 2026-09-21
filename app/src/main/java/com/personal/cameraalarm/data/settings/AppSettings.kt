package com.personal.cameraalarm.data.settings

import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.schedule.ActiveTimeRange
import com.personal.cameraalarm.schedule.ScheduleMode

data class AppSettings(
    val monitoringEnabled: Boolean = false,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val alarmDelayMs: Long = 1000L,
    val cooldownMs: Long = SettingsDefaults.COOLDOWN_MS,
    val vibrationEnabled: Boolean = true,
    val fullScreenEnabled: Boolean = false,
    val alarmSoundKey: String = AlarmSoundCatalog.OFFICIAL_PROFILE_DEFAULT_KEY,
    val scheduleMode: ScheduleMode = ScheduleMode.CUSTOM,
    val scheduleRanges: List<ActiveTimeRange> = SettingsDefaults.scheduleRanges(),
    val language: String = "vi"
)

object SettingsDefaults {
    const val COOLDOWN_MS = 600_000L
    const val LEGACY_COOLDOWN_MS = 10_000L
    const val OVERNIGHT_START_MINUTES = 22 * 60 + 30
    const val OVERNIGHT_END_MINUTES = 6 * 60
    const val DEFAULT_RANGE_ID = "official-default-overnight"

    fun scheduleRanges(): List<ActiveTimeRange> = listOf(
        ActiveTimeRange(
            id = DEFAULT_RANGE_ID,
            startMinutes = OVERNIGHT_START_MINUTES,
            endMinutes = OVERNIGHT_END_MINUTES,
            enabled = true
        )
    )

    fun official(): AppSettings = AppSettings()
}
