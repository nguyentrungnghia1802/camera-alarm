package com.personal.cameraalarm.app

import android.content.Context
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.util.AndroidClock
import com.personal.cameraalarm.permission.ExactAlarmAccess
import com.personal.cameraalarm.permission.ReadinessRepository

class AppContainer(context: Context) {
    val listenerConnection = ListenerConnectionState()
    val runtimeDiagnostics = RuntimeDiagnostics()
    val exactAlarmAccess = ExactAlarmAccess(context)
    val stateStore = DataStoreAlarmStateStore(context)
    var triggerConfiguration = TriggerConfiguration(false, null, emptyList())
    var alarmPolicy = AlarmPolicy()
    val scheduler = AndroidAlarmScheduler(context)
    val readiness = ReadinessRepository(context, exactAlarmAccess, listenerConnection) { triggerConfiguration }
    val coordinator = AlarmCoordinator(AndroidClock, scheduler, stateStore, { alarmPolicy })
    val pipeline = TriggerPipeline(AndroidClock, { triggerConfiguration }, TtlDuplicateGuard(), coordinator,
        TriggerHistory { notification, decision, _ ->
            if (com.personal.cameraalarm.BuildConfig.DEBUG) android.util.Log.d("CameraAlarm", "decision=$decision source=${notification.packageName}")
        })
}
