package com.personal.cameraalarm.alarm

enum class AlarmStartDecision {
    ACCEPT,
    REPLACE_ACTIVE_TEST,
    REJECT
}

object AlarmRuntimeOwnership {
    fun canStopService(state: AlarmState, stoppedToken: AlarmToken?): Boolean {
        val owner = (state as? AlarmState.Ringing)?.trigger?.alarmToken
        return owner == null || owner == stoppedToken
    }

    fun decideStart(
        activeToken: AlarmToken?,
        requestedToken: AlarmToken,
        requestedIsTest: Boolean
    ): AlarmStartDecision {
        if (activeToken == null || activeToken == requestedToken) return AlarmStartDecision.ACCEPT
        if (!requestedIsTest && StopAlarmReceiver.isTestAlarm(activeToken)) {
            return AlarmStartDecision.REPLACE_ACTIVE_TEST
        }
        return AlarmStartDecision.REJECT
    }

    fun canStartTest(state: AlarmState, activeTestToken: AlarmToken?): Boolean =
        state !is AlarmState.Pending && state !is AlarmState.Ringing && activeTestToken == null

    fun stopToken(state: AlarmState, activeTestToken: AlarmToken?): AlarmToken? =
        (state as? AlarmState.Ringing)?.trigger?.alarmToken ?: activeTestToken
}
