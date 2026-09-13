package com.personal.cameraalarm.alarm.sound

import androidx.annotation.RawRes
import com.personal.cameraalarm.R

data class AlarmSound(
    val key: String,
    val displayName: String,
    @RawRes val rawResourceId: Int
)

object AlarmSoundCatalog {
    const val DEFAULT_KEY = "alarm_default"

    val allSounds: List<AlarmSound> = listOf(
        AlarmSound(
            key = DEFAULT_KEY,
            displayName = "Default Alarm",
            rawResourceId = R.raw.alarm_default
        ),
        AlarmSound(
            key = "alarm_siren",
            displayName = "Siren",
            rawResourceId = R.raw.alarm_siren
        ),
        AlarmSound(
            key = "alarm_warning",
            displayName = "Warning",
            rawResourceId = R.raw.alarm_warning
        ),
        AlarmSound(
            key = "alarm_loud",
            displayName = "Loud Alarm",
            rawResourceId = R.raw.alarm_loud
        )
    )

    private val soundsByKey = allSounds.associateBy { it.key }

    fun defaultSound(): AlarmSound = soundsByKey.getValue(DEFAULT_KEY)

    fun resolve(key: String?): AlarmSound {
        if (key.isNullOrBlank()) return defaultSound()
        return soundsByKey[key] ?: defaultSound()
    }

    fun isValidKey(key: String?): Boolean = !key.isNullOrBlank() && soundsByKey.containsKey(key)
}
