package com.personal.cameraalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val token = intent.getStringExtra(EXTRA_TOKEN)?.takeIf(String::isNotBlank) ?: return
        val pending = goAsync()
        val app = context.applicationContext as CameraAlarmApp
        app.scope.launch {
            try {
                withTimeout(8_000) {
                    if (!app.container.exactAlarmAccess.isGranted()) return@withTimeout
                    val state = app.container.stateStore.read()
                    if (state !is AlarmState.Pending || state.trigger.alarmToken.value != token) return@withTimeout
                    app.container.coordinator.onExactAlarmFired(state.trigger)
                    val current = app.container.stateStore.read()
                    if (current !is AlarmState.Ringing || current.trigger.alarmToken.value != token) return@withTimeout
                    val service = Intent(context, CameraAlarmService::class.java)
                        .setAction(CameraAlarmService.ACTION_START)
                        .putExtra(EXTRA_TOKEN, token)
                    ContextCompat.startForegroundService(context, service)
                    if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "receiver started alarm service token=$token")
                }
            } catch (e: Exception) {
                Log.e("CameraAlarm", "Alarm receiver failed for token=$token", e)
            } finally { pending.finish() }
        }
    }
    companion object {
        const val ACTION_FIRE = "com.personal.cameraalarm.action.FIRE_ALARM"
        const val EXTRA_TOKEN = "alarm_token"
    }
}
