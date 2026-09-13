package com.personal.cameraalarm.alarm

@JvmInline value class AlarmToken(val value: String)

data class TriggerSnapshot(
    val alarmToken: AlarmToken,
    val sourcePackage: String,
    val notificationKey: String,
    val ruleId: String,
    val title: String?,
    val textPreview: String?,
    val receivedAtEpochMs: Long
)

data class AlarmPolicy(val delayMs: Long = 1_000, val cooldownMs: Long = 10_000, val vibrationEnabled: Boolean = true) {
    init { require(delayMs >= 0 && cooldownMs >= 0) }
}

sealed interface AlarmState {
    data object Idle : AlarmState
    data class Pending(val trigger: TriggerSnapshot, val scheduledAtEpochMs: Long) : AlarmState
    data class Ringing(val trigger: TriggerSnapshot, val startedAtEpochMs: Long) : AlarmState
    data class Cooldown(val untilEpochMs: Long, val lastAlarmToken: AlarmToken) : AlarmState
}

sealed interface AlarmEvent {
    data class ValidTrigger(val trigger: TriggerSnapshot) : AlarmEvent
    data class ExactAlarmFired(val trigger: TriggerSnapshot) : AlarmEvent
    data class StopRequested(val alarmToken: AlarmToken?) : AlarmEvent
    data class ScheduleFailed(val alarmToken: AlarmToken) : AlarmEvent
}

enum class SuppressionReason { PENDING, RINGING, COOLDOWN, STALE_ALARM }
sealed interface AlarmEffect {
    data class ScheduleExact(val trigger: TriggerSnapshot, val triggerAtEpochMs: Long) : AlarmEffect
    data class StartRinging(val trigger: TriggerSnapshot) : AlarmEffect
    data class StopRuntime(val alarmToken: AlarmToken?) : AlarmEffect
    data class CancelExact(val alarmToken: AlarmToken) : AlarmEffect
    data class RecordSuppression(val reason: SuppressionReason) : AlarmEffect
    data class RecordFailure(val reason: String) : AlarmEffect
}
data class AlarmTransition(val nextState: AlarmState, val effects: List<AlarmEffect> = emptyList())
