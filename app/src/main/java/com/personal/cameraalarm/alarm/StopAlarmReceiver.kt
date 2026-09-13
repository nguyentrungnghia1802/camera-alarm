package com.personal.cameraalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP) return
        val token = intent.getStringExtra(AlarmReceiver.EXTRA_TOKEN)?.takeIf(String::isNotBlank)?.let(::AlarmToken) ?: return
        val pending = goAsync()
        val app = context.applicationContext as CameraAlarmApp
        app.scope.launch {
            try {
                app.container.coordinator.onStopRequested(token)
                val state = app.container.stateStore.read()
                if (state !is AlarmState.Cooldown || state.lastAlarmToken != token) return@launch
                val stop = Intent(context, CameraAlarmService::class.java)
                    .setAction(CameraAlarmService.ACTION_STOP).putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
                ContextCompat.startForegroundService(context, stop)
            } catch (e: Exception) { Log.e("CameraAlarm", "STOP failed for token=${token.value}", e) }
            finally { pending.finish() }
        }
    }
    companion object { const val ACTION_STOP = "com.personal.cameraalarm.action.STOP_REQUESTED" }
}
