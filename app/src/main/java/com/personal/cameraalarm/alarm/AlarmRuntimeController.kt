package com.personal.cameraalarm.alarm

/** Call on the service main thread; no Android API is needed to test idempotency. */
class AlarmRuntimeController(private val player: AlarmPlayer, private val vibration: VibrationController) {
    var activeToken: AlarmToken? = null
        private set

    fun start(token: AlarmToken, vibrationEnabled: Boolean, soundKey: String? = null): List<String> {
        if (activeToken != null) return emptyList()
        activeToken = token
        val errors = mutableListOf<String>()
        player.start(soundKey).exceptionOrNull()?.let { errors += "audio: ${it.message ?: it.javaClass.simpleName}" }
        if (vibrationEnabled) try {
            vibration.startRepeating()
        } catch (e: RuntimeException) {
            errors += "vibration: ${e.message ?: e.javaClass.simpleName}"
        }
        return errors
    }

    fun stop(token: AlarmToken?): List<String> {
        if (activeToken == null || (token != null && token != activeToken)) return emptyList()
        activeToken = null
        val errors = mutableListOf<String>()
        try {
            player.stop()
        } catch (e: RuntimeException) {
            errors += "audio stop: ${e.message ?: e.javaClass.simpleName}"
        }
        try {
            vibration.stop()
        } catch (e: RuntimeException) {
            errors += "vibration stop: ${e.message ?: e.javaClass.simpleName}"
        }
        return errors
    }
}
