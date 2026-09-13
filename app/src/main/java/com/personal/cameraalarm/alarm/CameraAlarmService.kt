package com.personal.cameraalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.graphics.drawable.Icon
import android.util.Log
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CameraAlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as CameraAlarmApp
    private val runtime by lazy { AlarmRuntimeController(AndroidAlarmPlayer(this), AndroidVibrationController(this)) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(AlarmReceiver.EXTRA_TOKEN)?.takeIf(String::isNotBlank)?.let(::AlarmToken)
        if (intent?.action == ACTION_STOP) {
            if (runtime.activeToken != null && runtime.activeToken != token) return START_NOT_STICKY
            runtime.stop(token).forEach { recordError(token, it) }
            if (token != null && StopAlarmReceiver.isTestAlarm(token)) {
                app.container.testAlarmToken.compareAndSet(token, null)
            }
            if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "runtime stopped token=${token?.value}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || token == null) { stopSelf(); return START_NOT_STICKY }
        if (runtime.activeToken != null && runtime.activeToken != token) return START_NOT_STICKY
        app.container.soundPreviewController.stop()
        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)
        try { promote(token, null) } catch (e: RuntimeException) {
            recordError(token, "foreground: ${e.message ?: e.javaClass.simpleName}")
            if (isTest) app.container.testAlarmToken.compareAndSet(token, null)
            else scope.launch { app.container.coordinator.onStopRequested(token) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (isTest) {
            val testTrigger = TriggerSnapshot(token, "com.personal.cameraalarm", "test_key", "test_rule", "Test Alarm", "Testing camera alarm sound & vibration", System.currentTimeMillis())
            try { promote(token, testTrigger) } catch (e: RuntimeException) {
                recordError(token, "foreground update: ${e.message ?: e.javaClass.simpleName}")
                app.container.testAlarmToken.compareAndSet(token, null)
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
            }
            val soundKey = kotlinx.coroutines.runBlocking {
                try { app.container.settingsRepository.current().alarmSoundKey } catch (_: Exception) { null }
            }
            runtime.start(token, app.container.alarmPolicy.vibrationEnabled, soundKey).forEach { recordError(token, it) }
            return START_NOT_STICKY
        }
        scope.launch {
            val state = try { app.container.stateStore.read() } catch (e: Exception) {
                recordError(token, "state read: ${e.message ?: e.javaClass.simpleName}"); null
            }
            if (state !is AlarmState.Ringing || state.trigger.alarmToken != token) {
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return@launch
            }
            if (runtime.activeToken == null) {
                try { promote(token, state.trigger) } catch (e: RuntimeException) {
                    recordError(token, "foreground update: ${e.message ?: e.javaClass.simpleName}")
                    app.container.coordinator.onStopRequested(token)
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return@launch
                }
                val soundKey = try { app.container.settingsRepository.current().alarmSoundKey } catch (_: Exception) { null }
                runtime.start(token, app.container.alarmPolicy.vibrationEnabled, soundKey).forEach { recordError(token, it) }
                if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "runtime started token=${token.value}")
                val latest = try { app.container.stateStore.read() } catch (e: Exception) {
                    recordError(token, "state recheck: ${e.message ?: e.javaClass.simpleName}"); null
                }
                if (latest !is AlarmState.Ringing || latest.trigger.alarmToken != token) {
                    runtime.stop(token).forEach { recordError(token, it) }
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun promote(token: AlarmToken, trigger: TriggerSnapshot?) {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Camera alarms", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
        val stop = PendingIntent.getBroadcast(this, 1,
            Intent(this, StopAlarmReceiver::class.java).setAction(StopAlarmReceiver.ACTION_STOP)
                .putExtra(AlarmReceiver.EXTRA_TOKEN, token.value),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Camera Alert")
            .setContentText(trigger?.title ?: trigger?.textPreview ?: "Camera notification detected")
            .setWhen(trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setAutoCancel(false)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "STOP", stop).build())

        val canFullScreen = app.container.readiness.canUseFullScreenIntent()
        val fullScreenEnabled = kotlinx.coroutines.runBlocking {
            try { app.container.settingsRepository.current().fullScreenEnabled } catch (_: Exception) { false }
        }
        if (canFullScreen && fullScreenEnabled) {
            val fullScreenIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
            }
            val pendingFullScreen = PendingIntent.getActivity(
                this,
                2,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(pendingFullScreen, true)
        }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(NOTIFICATION_ID, notification)
    }
    private fun recordError(token: AlarmToken?, error: String) {
        Log.e("CameraAlarm", "alarm runtime error token=${token?.value}: $error")
        app.container.runtimeDiagnostics.record(error)
    }
    override fun onDestroy() {
        val token = runtime.activeToken
        runtime.stop(null).forEach { recordError(token, it) }
        if (token != null && StopAlarmReceiver.isTestAlarm(token)) {
            app.container.testAlarmToken.compareAndSet(token, null)
        } else if (token != null) {
            app.scope.launch { app.container.coordinator.onStopRequested(token) }
        }
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        const val ACTION_START = "com.personal.cameraalarm.action.START_ALARM"
        const val ACTION_STOP = "com.personal.cameraalarm.action.STOP_ALARM"
        const val EXTRA_IS_TEST = "extra_is_test"
        const val CHANNEL = "alarm_runtime"
        private const val NOTIFICATION_ID = 1
    }
}
