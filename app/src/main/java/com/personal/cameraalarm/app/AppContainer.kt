package com.personal.cameraalarm.app

import android.content.Context
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.util.AndroidClock

class AppContainer(context: Context) {
    val listenerConnection = ListenerConnectionState()
    var triggerConfiguration = TriggerConfiguration(false, null, emptyList())
    val coordinator = AlarmCoordinator(AndroidClock, object : AlarmScheduler {
        override fun canScheduleExactAlarms() = false
        override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.ExactAlarmPermissionMissing
        override fun cancel(token: AlarmToken) = Unit
    }, InMemoryAlarmStateStore(), { AlarmPolicy() })
    val pipeline = TriggerPipeline(AndroidClock, { triggerConfiguration }, TtlDuplicateGuard(), coordinator,
        TriggerHistory { notification, decision, _ ->
            if (com.personal.cameraalarm.BuildConfig.DEBUG) android.util.Log.d("CameraAlarm", "decision=$decision source=${notification.packageName}")
        })
}
