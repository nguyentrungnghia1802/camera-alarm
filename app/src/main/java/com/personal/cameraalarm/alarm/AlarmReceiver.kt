package com.personal.cameraalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val token = intent.getStringExtra(EXTRA_TOKEN)?.takeIf(String::isNotBlank) ?: return
        val receiverFiredElapsedMs = SystemClock.elapsedRealtime()
        Log.i("CameraAlarm", "ALARM_RECEIVER_FIRED: token=$token elapsed_ms=$receiverFiredElapsedMs")
        val pending = goAsync()
        val app = context.applicationContext as CameraAlarmApp

        val pm = context.getSystemService(PowerManager::class.java)
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraAlarm:AlarmReceiverWakeLock")
        wakeLock?.acquire(10_000)

        app.scope.launch {
            try {
                withTimeout(8_000) {
                    if (!app.container.exactAlarmAccess.isGranted()) return@withTimeout
                    val state = app.container.stateStore.read()
                    if (state !is AlarmState.Pending || state.trigger.alarmToken.value != token) return@withTimeout
                    val trigger = state.trigger
                    app.container.coordinator.onExactAlarmFired(trigger)
                    val current = app.container.stateStore.read()
                    if (current !is AlarmState.Ringing || current.trigger.alarmToken.value != token) return@withTimeout
                    val service = Intent(context, CameraAlarmService::class.java)
                        .setAction(CameraAlarmService.ACTION_START)
                        .putExtra(EXTRA_TOKEN, token)
                        .putExtra(CameraAlarmService.EXTRA_SOURCE, trigger.sourcePackage)
                        .putExtra(CameraAlarmService.EXTRA_TITLE, trigger.title)
                        .putExtra(CameraAlarmService.EXTRA_PREVIEW, trigger.textPreview)
                        .putExtra(CameraAlarmService.EXTRA_TIME, trigger.receivedAtEpochMs)
                        .putExtra(CameraAlarmService.EXTRA_RULE, trigger.ruleId)
                        .putExtra(CameraAlarmService.EXTRA_KEY, trigger.notificationKey)
                    ContextCompat.startForegroundService(context, service)
                    Log.i(
                        "CameraAlarm",
                        "ALARM_TIMING service_requested_ms=${SystemClock.elapsedRealtime() - receiverFiredElapsedMs} token=$token"
                    )

                    val fullScreenEnabled = app.container.alarmRuntimeConfig.current().fullScreenEnabled

                    if (fullScreenEnabled && app.container.readiness.canUseFullScreenIntent()) {
                        val directIntent = Intent(context, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            data = android.net.Uri.parse("cameraalarm://alarm_full/${token}")
                            putExtra(EXTRA_TOKEN, token)
                            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger.sourcePackage)
                            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger.title)
                            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger.textPreview)
                            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger.receivedAtEpochMs)
                        }
                        val options = android.app.ActivityOptions.makeBasic()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            options.setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        }
                        try {
                            context.startActivity(directIntent, options.toBundle())
                            Log.i("CameraAlarm", "AlarmReceiver direct startActivity succeeded for token=$token")
                        } catch (e: Exception) {
                            Log.w("CameraAlarm", "AlarmReceiver direct startActivity failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraAlarm", "Alarm receiver failed for token=$token", e)
                app.container.runtimeDiagnostics.record("receiver: ${e.message ?: e.javaClass.simpleName}")
                try { app.container.coordinator.onStopRequested(AlarmToken(token)) } catch (cleanup: Exception) {
                    Log.e("CameraAlarm", "Alarm receiver cleanup failed for token=$token", cleanup)
                }
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
                pending.finish()
            }
        }
    }
    companion object {
        const val ACTION_FIRE = "com.personal.cameraalarm.action.FIRE_ALARM"
        const val EXTRA_TOKEN = "alarm_token"
    }
}
