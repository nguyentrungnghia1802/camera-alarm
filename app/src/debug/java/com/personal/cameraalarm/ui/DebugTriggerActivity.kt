package com.personal.cameraalarm.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

/** Debug-only injection through the production trigger pipeline. */
class DebugTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        inject(intent)
    }
    private fun inject(incomingIntent: Intent) {
        val app = application as CameraAlarmApp
        app.scope.launch { DebugTriggerInjector.inject(app, incomingIntent) }
        finish()
    }
}
