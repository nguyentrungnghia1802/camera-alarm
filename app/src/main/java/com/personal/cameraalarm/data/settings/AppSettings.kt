package com.personal.cameraalarm.data.settings

import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.schedule.ActiveTimeRange
import com.personal.cameraalarm.schedule.ScheduleMode

data class AppSettings(
    val monitoringEnabled: Boolean = false,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val alarmDelayMs: Long = 1000L,
    val cooldownMs: Long = 10000L,
    val vibrationEnabled: Boolean = true,
    val fullScreenEnabled: Boolean = false,
    val alarmSoundKey: String = AlarmSoundCatalog.DEFAULT_KEY,
    val scheduleMode: ScheduleMode = ScheduleMode.ALWAYS_ACTIVE,
    val scheduleRanges: List<ActiveTimeRange> = emptyList(),
    val language: String = "vi"
)
