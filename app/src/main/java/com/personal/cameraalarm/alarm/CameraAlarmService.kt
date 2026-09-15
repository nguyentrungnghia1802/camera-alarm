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

        Log.i("CameraAlarm", "FOREGROUND_SERVICE_STARTED: token=${token.value}")
        app.container.soundPreviewController.stop()

        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)
        val initialTrigger = if (isTest) {
            val configuredSource = app.container.triggerConfiguration.sourcePackage
            TriggerSnapshot(
                token,
                configuredSource ?: "com.personal.fakecamera",
                "test_key",
                "test_rule",
                getString(com.personal.cameraalarm.R.string.test_alarm_title),
                getString(com.personal.cameraalarm.R.string.test_alarm_preview),
                System.currentTimeMillis()
            )
        } else {
            val src = intent.getStringExtra(EXTRA_SOURCE)
            if (src != null) {
                TriggerSnapshot(
                    token,
                    src,
                    intent.getStringExtra(EXTRA_KEY) ?: "",
                    intent.getStringExtra(EXTRA_RULE) ?: "",
                    intent.getStringExtra(EXTRA_TITLE),
                    intent.getStringExtra(EXTRA_PREVIEW),
                    intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
                )
            } else null
        }

        // 1. Promote to foreground service immediately
        try {
            promote(token, initialTrigger)
        } catch (e: RuntimeException) {
            recordError(token, "foreground: ${e.message ?: e.javaClass.simpleName}")
            if (isTest) app.container.testAlarmToken.compareAndSet(token, null)
            else scope.launch { app.container.coordinator.onStopRequested(token) }
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Start audio & vibration immediately
        val soundKey = kotlinx.coroutines.runBlocking {
            try { app.container.settingsRepository.current().alarmSoundKey } catch (_: Exception) { null }
        }
        runtime.start(token, app.container.alarmPolicy.vibrationEnabled, soundKey).forEach { recordError(token, it) }

        // 3. Launch full-screen AlarmActivity (if enabled and permitted)
        launchAlarmActivity(token, initialTrigger)

        // 4. Verify state asynchronously if initial trigger was not provided via intent
        if (!isTest && initialTrigger == null) {
            scope.launch {
                val state = try { app.container.stateStore.read() } catch (e: Exception) {
                    recordError(token, "state read: ${e.message ?: e.javaClass.simpleName}"); null
                }
                if (state !is AlarmState.Ringing || state.trigger.alarmToken != token) {
                    runtime.stop(token).forEach { recordError(token, it) }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                try {
                    promote(token, state.trigger)
                    launchAlarmActivity(token, state.trigger)
                } catch (e: RuntimeException) {
                    recordError(token, "foreground update: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun promote(token: AlarmToken, trigger: TriggerSnapshot?) {
        val manager = getSystemService(NotificationManager::class.java)
        val channelName = getString(com.personal.cameraalarm.R.string.notification_channel_alarm)
        val channelDesc = getString(com.personal.cameraalarm.R.string.notification_channel_alarm_desc)
        val channel = NotificationChannel(CHANNEL, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = channelDesc
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)

        // Wake screen from black when alarm triggers
        val pm = getSystemService(android.os.PowerManager::class.java)
        val screenLock = pm?.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "CameraAlarm:ScreenWakeLock"
        )
        screenLock?.acquire(3_000)

        val stop = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, StopAlarmReceiver::class.java)
                .setAction(StopAlarmReceiver.ACTION_STOP)
                .putExtra(AlarmReceiver.EXTRA_TOKEN, token.value),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openCameraIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("cameraalarm://alarm_open/${token.value}")
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_AUTO_OPEN_CAMERA, true)
        }
        val openCameraPending = PendingIntent.getActivity(
            this,
            (token.value + "_open").hashCode(),
            openCameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = getString(com.personal.cameraalarm.R.string.notification_alarm_title)
        val notifText = trigger?.title?.takeIf { it.isNotBlank() }
            ?: trigger?.textPreview?.takeIf { it.isNotBlank() }
            ?: getString(com.personal.cameraalarm.R.string.notification_alarm_fallback_text)

        val stopActionTitle = getString(com.personal.cameraalarm.R.string.btn_stop_alarm)
        val openCameraTitle = getString(com.personal.cameraalarm.R.string.btn_open_camera)

        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.personal.cameraalarm.R.mipmap.ic_launcher)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setWhen(trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_camera),
                    openCameraTitle,
                    openCameraPending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    stopActionTitle,
                    stop
                ).build()
            )

        val pendingFullScreen = createFullScreenPendingIntent(token, trigger)
        if (pendingFullScreen != null) {
            builder.setFullScreenIntent(pendingFullScreen, true)
            builder.setContentIntent(pendingFullScreen)
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createFullScreenPendingIntent(token: AlarmToken, trigger: TriggerSnapshot?): PendingIntent? {
        val canFullScreen = app.container.readiness.canUseFullScreenIntent()
        val fullScreenEnabled = kotlinx.coroutines.runBlocking {
            try { app.container.settingsRepository.current().fullScreenEnabled } catch (_: Exception) { true }
        }
        if (!canFullScreen || !fullScreenEnabled) return null

        val fullScreenIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("cameraalarm://alarm/${token.value}")
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
        }

        val options = android.app.ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.setPendingIntentBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }

        return PendingIntent.getActivity(
            this,
            token.value.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options.toBundle()
        )
    }

    private fun launchAlarmActivity(token: AlarmToken, trigger: TriggerSnapshot?) {
        val pending = createFullScreenPendingIntent(token, trigger) ?: return
        Log.i("CameraAlarm", "FULLSCREEN_INTENT_SENT: token=${token.value}")

        // Attempt direct launch when screen is on / unlocked
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val launchOpts = android.app.ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pending.send(this, 0, null, null, null, null, launchOpts.toBundle())
            } else {
                pending.send()
            }
        } catch (e: Exception) {
            try {
                val directIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    data = android.net.Uri.parse("cameraalarm://alarm/${token.value}")
                    putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
                }
                startActivity(directIntent)
            } catch (e2: Exception) {
                if (com.personal.cameraalarm.BuildConfig.DEBUG) {
                    Log.d("CameraAlarm", "Direct activity start fallback skipped: ${e2.message}")
                }
            }
        }
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
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PREVIEW = "extra_preview"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_RULE = "extra_rule"
        const val EXTRA_KEY = "extra_key"
        const val CHANNEL = "alarm_runtime"
        private const val NOTIFICATION_ID = 1
    }
}
