package com.personal.cameraalarm.alarm

import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog

data class AlarmRuntimeConfig(
    val vibrationEnabled: Boolean = true,
    val fullScreenEnabled: Boolean = false,
    val soundKey: String = AlarmSoundCatalog.DEFAULT_KEY
)

class AlarmRuntimeConfigCache(initial: AlarmRuntimeConfig = AlarmRuntimeConfig()) {
    @Volatile
    private var snapshot = initial

    fun current(): AlarmRuntimeConfig = snapshot

    fun update(config: AlarmRuntimeConfig) {
        snapshot = config
    }
}
