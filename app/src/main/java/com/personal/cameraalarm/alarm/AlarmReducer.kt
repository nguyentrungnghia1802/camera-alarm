package com.personal.cameraalarm.alarm

object AlarmReducer {
    fun reduce(state: AlarmState, event: AlarmEvent, nowEpochMs: Long, settings: AlarmPolicy): AlarmTransition = when (event) {
        is AlarmEvent.ValidTrigger -> when (state) {
            AlarmState.Idle -> schedule(event.trigger, nowEpochMs, settings)
            is AlarmState.Pending -> AlarmTransition(state, listOf(AlarmEffect.RecordSuppression(SuppressionReason.PENDING)))
            is AlarmState.Ringing -> AlarmTransition(state, listOf(AlarmEffect.RecordSuppression(SuppressionReason.RINGING)))
            is AlarmState.Cooldown -> if (nowEpochMs >= state.untilEpochMs) schedule(event.trigger, nowEpochMs, settings)
                else AlarmTransition(state, listOf(AlarmEffect.RecordSuppression(SuppressionReason.COOLDOWN)))
        }
        is AlarmEvent.ExactAlarmFired -> if (state is AlarmState.Pending && state.trigger.alarmToken == event.trigger.alarmToken) {
            AlarmTransition(AlarmState.Ringing(state.trigger, nowEpochMs), listOf(AlarmEffect.StartRinging(state.trigger)))
        } else AlarmTransition(state, listOf(AlarmEffect.RecordSuppression(SuppressionReason.STALE_ALARM)))
        is AlarmEvent.StopRequested -> when (state) {
            is AlarmState.Ringing -> if (event.alarmToken == null || event.alarmToken == state.trigger.alarmToken)
                AlarmTransition(AlarmState.Cooldown(nowEpochMs + settings.cooldownMs, state.trigger.alarmToken), listOf(AlarmEffect.StopRuntime(state.trigger)))
                else AlarmTransition(state)
            is AlarmState.Pending -> if (event.alarmToken == null || event.alarmToken == state.trigger.alarmToken)
                AlarmTransition(AlarmState.Cooldown(nowEpochMs + settings.cooldownMs, state.trigger.alarmToken), listOf(AlarmEffect.CancelExact(state.trigger)))
                else AlarmTransition(state)
            else -> AlarmTransition(state)
        }
        is AlarmEvent.ScheduleFailed -> if (state is AlarmState.Pending && state.trigger.alarmToken == event.alarmToken)
            AlarmTransition(AlarmState.Idle, listOf(AlarmEffect.RecordFailure("Exact alarm schedule failed", state.trigger)))
            else AlarmTransition(state)
    }
    private fun schedule(trigger: TriggerSnapshot, now: Long, policy: AlarmPolicy): AlarmTransition {
        val at = now + policy.delayMs
        return AlarmTransition(AlarmState.Pending(trigger, at), listOf(AlarmEffect.ScheduleExact(trigger, at)))
    }
}
