package com.personal.cameraalarm.data.history

import com.personal.cameraalarm.alarm.AlarmEffect
import com.personal.cameraalarm.alarm.TriggerSnapshot

object AlarmHistoryEventFactory {
    fun fromEffect(
        effect: AlarmEffect,
        createdAtEpochMs: Long
    ): AlertEventEntity? = when (effect) {
        is AlarmEffect.StartRinging -> lifecycleEvent(
            effect.trigger,
            createdAtEpochMs,
            decision = "ALARM_FIRED"
        )
        is AlarmEffect.StopRuntime -> lifecycleEvent(
            effect.trigger,
            createdAtEpochMs,
            decision = "ALARM_STOPPED"
        )
        is AlarmEffect.CancelExact -> lifecycleEvent(
            effect.trigger,
            createdAtEpochMs,
            decision = "ALARM_CANCELLED"
        )
        is AlarmEffect.RecordFailure -> lifecycleEvent(
            effect.trigger, createdAtEpochMs, "SCHEDULE_FAILED"
        ).copy(details = effect.reason)
        else -> null
    }

    private fun lifecycleEvent(
        trigger: TriggerSnapshot,
        createdAtEpochMs: Long,
        decision: String
    ) = AlertEventEntity(
        createdAtEpochMs = createdAtEpochMs,
        sourcePackage = trigger.sourcePackage,
        notificationKey = trigger.notificationKey,
        title = trigger.title,
        textPreview = trigger.textPreview,
        normalizedHash = null,
        decision = decision,
        ruleId = trigger.ruleId,
        alarmToken = trigger.alarmToken.value,
        details = null
    )
}
