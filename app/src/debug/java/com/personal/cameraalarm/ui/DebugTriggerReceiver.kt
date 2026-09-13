package com.personal.cameraalarm.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class DebugTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as CameraAlarmApp
        app.scope.launch {
            try { DebugTriggerInjector.inject(app, intent) } finally { pending.finish() }
        }
    }
}
