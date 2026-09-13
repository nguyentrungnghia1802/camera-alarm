package com.personal.cameraalarm.ui

import android.content.Intent
import android.util.Log
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.alarm.AlarmPolicy
import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerConfiguration
import com.personal.cameraalarm.trigger.TriggerRule

/** Debug-only representative event, always routed through TriggerPipeline. */
object DebugTriggerInjector {
    suspend fun inject(app: CameraAlarmApp, intent: Intent) {
        app.container.alarmPolicy = AlarmPolicy(delayMs = intent.getLongExtra("delay_ms", 1_000).coerceIn(0, 5_000))
        val source = "com.personal.cameraalarm.debug"
        app.container.triggerConfiguration = TriggerConfiguration(true, source,
            listOf(TriggerRule("debug", "Debug person", true, source, MatchMode.CONTAINS_ANY, listOf("human"), 0, 0)))
        val readiness = app.container.readiness.snapshot()
        val volume = app.container.readiness.volumeStatus()
        Log.d("CameraAlarm", "debug readiness exact=${readiness.exactAlarmGranted} notifications=${readiness.postNotificationsGranted} alarmVolume=${volume.current}/${volume.maximum} min=${volume.minimum} alarmVolumeNonZero=${readiness.alarmVolumeNonZero}")
        val event = IncomingNotification(intent.getStringExtra("key").orEmpty(), source, 1, null,
            System.currentTimeMillis(), "Human detected", null, null, emptyList(), null)
        app.container.pipeline.process(event)
    }
}
