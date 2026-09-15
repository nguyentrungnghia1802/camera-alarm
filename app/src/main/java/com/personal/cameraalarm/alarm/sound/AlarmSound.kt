package com.personal.cameraalarm.alarm.sound

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.personal.cameraalarm.R

data class AlarmSound(
    val key: String,
    val displayName: String,
    @RawRes val rawResourceId: Int,
    @StringRes val displayNameResId: Int = R.string.sound_default
)

object AlarmSoundCatalog {
    const val DEFAULT_KEY = "alarm_default"

    val allSounds: List<AlarmSound> = listOf(
        AlarmSound(
            key = DEFAULT_KEY,
            displayName = "Default Alarm",
            rawResourceId = R.raw.alarm_default,
            displayNameResId = R.string.sound_default
        ),
        AlarmSound(
            key = "alarm_siren",
            displayName = "Siren",
            rawResourceId = R.raw.alarm_siren,
            displayNameResId = R.string.sound_siren
        ),
        AlarmSound(
            key = "alarm_warning",
            displayName = "Warning",
            rawResourceId = R.raw.alarm_warning,
            displayNameResId = R.string.sound_warning
        ),
        AlarmSound(
            key = "alarm_loud",
            displayName = "Loud Alarm",
            rawResourceId = R.raw.alarm_loud,
            displayNameResId = R.string.sound_loud
        ),
        AlarmSound(
            key = "alarm_warning_aloud",
            displayName = "Loud Warning 1",
            rawResourceId = R.raw.alarm_warning_aloud,
            displayNameResId = R.string.sound_warning_aloud
        ),
        AlarmSound(
            key = "alarm_warning_aloud_2",
            displayName = "Loud Warning 2",
            rawResourceId = R.raw.alarm_warning_aloud_2,
            displayNameResId = R.string.sound_warning_aloud_2
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
