package com.personal.cameraalarm.ui

import android.app.Activity
import android.os.Bundle
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.alarm.AlarmPolicy
import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerConfiguration
import com.personal.cameraalarm.trigger.TriggerRule
import kotlinx.coroutines.launch

/** Debug-only injection through the production trigger pipeline. */
class DebugTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CameraAlarmApp
        app.container.alarmPolicy = AlarmPolicy(delayMs = intent.getLongExtra("delay_ms", 1_000).coerceIn(0, 5_000))
        val source = "com.personal.cameraalarm.debug"
        app.container.triggerConfiguration = TriggerConfiguration(true, source,
            listOf(TriggerRule("debug", "Debug person", true, source, MatchMode.CONTAINS_ANY, listOf("human"), 0, 0)))
        val event = IncomingNotification(intent.getStringExtra("key").orEmpty(), source, 1, null,
            System.currentTimeMillis(), "Human detected", null, null, emptyList(), null)
        app.scope.launch { app.container.pipeline.process(event) }
        finish()
    }
}
