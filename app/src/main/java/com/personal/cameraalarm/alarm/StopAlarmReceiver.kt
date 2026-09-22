package com.personal.cameraalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP) return
        val token = intent.getStringExtra(AlarmReceiver.EXTRA_TOKEN)?.takeIf(String::isNotBlank)?.let(::AlarmToken) ?: return
        Log.i("CameraAlarm", "STOP_RECEIVER_FIRED: token=${token.value}")
        // Test alarms have no coordinator state to persist. Dispatch their STOP
        // synchronously so it cannot be delayed behind unrelated application
        // coroutine work after a cold process/emulator start.
        if (isTestAlarm(token)) {
            startStopService(context, token)
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as CameraAlarmApp
        app.scope.launch {
            try {
                app.container.coordinator.onStopRequested(token)
                startStopService(context, token)
            } catch (e: Exception) { Log.e("CameraAlarm", "STOP failed for token=${token.value}", e) }
            finally { pending.finish() }
        }
    }
    private fun startStopService(context: Context, token: AlarmToken) {
        val stop = Intent(context, CameraAlarmService::class.java)
            .setAction(CameraAlarmService.ACTION_STOP)
            .putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
        // This command targets the already-running foreground service. Starting
        // a second foreground-service request for a STOP command can be deferred
        // by newer Android versions and also creates a promotion obligation the
        // STOP branch intentionally never fulfills.
        context.startService(stop)
    }

    companion object {
        /** User-initiated STOP must not queue behind background boot broadcasts. */
        fun intent(context: Context, token: String?): Intent = Intent(context, StopAlarmReceiver::class.java)
            .setAction(ACTION_STOP)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(AlarmReceiver.EXTRA_TOKEN, token)

        const val ACTION_STOP = "com.personal.cameraalarm.action.STOP_REQUESTED"
        internal fun isTestAlarm(token: AlarmToken): Boolean = token.value.startsWith("test-")
    }
}
