package com.personal.cameraalarm.alarm

sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data object ExactAlarmPermissionMissing : ScheduleResult
    data class Failed(val reason: String) : ScheduleResult
}
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult
    fun cancel(token: AlarmToken)
}
