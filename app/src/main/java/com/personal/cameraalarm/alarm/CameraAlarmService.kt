package com.personal.cameraalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
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
    private var destroyed = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serviceStartElapsedMs = SystemClock.elapsedRealtime()
        val token = intent?.getStringExtra(AlarmReceiver.EXTRA_TOKEN)?.takeIf(String::isNotBlank)?.let(::AlarmToken)
        if (intent?.action == ACTION_STOP) {
            if (runtime.activeToken != null && runtime.activeToken != token) return START_NOT_STICKY
            runtime.stop(token).forEach { recordError(token, it) }
            if (token != null && StopAlarmReceiver.isTestAlarm(token)) {
                app.container.testAlarmToken.compareAndSet(token, null)
            }
            // A new owner may already have queued START while its coroutine waits for the lock.
            // Release the old audio, but do not destroy the service needed by that owner.
            if (!AlarmRuntimeOwnership.canStopService(app.container.coordinator.state.value, token)) {
                return START_NOT_STICKY
            }
            if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "runtime stopped token=${token?.value}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || token == null) { stopSelf(); return START_NOT_STICKY }
        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)
        scope.launch {
            try {
                val accepted = if (isTest) app.container.coordinator.withTestOwnership {
                    if (!destroyed) startAccepted(intent, token, true, serviceStartElapsedMs)
                } else app.container.coordinator.withRingingOwnership(token) { trigger ->
                    // Snapshot from the owner, never trust an old queued service Intent.
                    if (!destroyed) startAccepted(intent, token, false, serviceStartElapsedMs, trigger)
                }
                if (destroyed && !isTest) app.container.coordinator.onStopRequested(token)
                if (!accepted && runtime.activeToken == null) stopSelfResult(startId)
            } catch (error: Exception) {
                recordError(token, "runtime claim: ${error.message}")
                app.container.coordinator.onStopRequested(token)
                if (runtime.activeToken == null) stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAccepted(
        intent: Intent, token: AlarmToken, isTest: Boolean, serviceStartElapsedMs: Long,
        ownedTrigger: TriggerSnapshot? = null
    ): Int {
        // Coordinator already authorized this production token. A different active runtime is
        // retired ownership (e.g. its queued STOP has not reached Main yet), and must be stopped first.
        if (!isTest && ownedTrigger != null && runtime.activeToken != null && runtime.activeToken != token) {
            val retired = runtime.activeToken
            runtime.stop(retired).forEach { recordError(retired, it) }
            if (retired != null) app.container.testAlarmToken.compareAndSet(retired, null)
        }
        when (AlarmRuntimeOwnership.decideStart(runtime.activeToken, token, isTest)) {
            AlarmStartDecision.REJECT -> return START_NOT_STICKY
            AlarmStartDecision.REPLACE_ACTIVE_TEST -> {
                val previousTestToken = runtime.activeToken
                runtime.stop(previousTestToken).forEach { recordError(previousTestToken, it) }
                if (previousTestToken != null) {
                    app.container.testAlarmToken.compareAndSet(previousTestToken, null)
                }
            }
            AlarmStartDecision.ACCEPT -> Unit
        }
        if (isTest && !AlarmRuntimeOwnership.canStartTest(
                app.container.coordinator.state.value,
                app.container.testAlarmToken.value
            )) return START_NOT_STICKY

        Log.i("CameraAlarm", "FOREGROUND_SERVICE_STARTED: token=${token.value}")
        app.container.soundPreviewController.stop()

        val runtimeConfig = app.container.alarmRuntimeConfig.current()
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
        } else if (ownedTrigger != null) ownedTrigger else {
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

        AlarmTrace.record("FOREGROUND_SERVICE_STARTED", token, initialTrigger)
        // 1. Promote to foreground service immediately
        try {
            promote(token, initialTrigger, runtimeConfig.fullScreenEnabled)
            Log.i(
                "CameraAlarm",
                "ALARM_TIMING foreground_ms=${SystemClock.elapsedRealtime() - serviceStartElapsedMs} token=${token.value}"
            )
        } catch (e: RuntimeException) {
            recordError(token, "foreground: ${e.message ?: e.javaClass.simpleName}")
            if (isTest) app.container.testAlarmToken.compareAndSet(token, null)
            else scope.launch { app.container.coordinator.onStopRequested(token) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (isTest) app.container.testAlarmToken.value = token

        // 2. Start audio & vibration immediately
        val errors = runtime.start(token, runtimeConfig.vibrationEnabled, runtimeConfig.soundKey)
        errors.forEach { recordError(token, it) }
        if (errors.none { it.startsWith("audio:") }) AlarmTrace.record("AUDIO_STARTED", token, initialTrigger)
        Log.i(
            "CameraAlarm",
            "ALARM_TIMING runtime_started_ms=${SystemClock.elapsedRealtime() - serviceStartElapsedMs} token=${token.value}"
        )

        // 3. Launch full-screen AlarmActivity (if enabled and permitted)
        if (isTest) {
            launchAcceptedTestActivity(token, initialTrigger)
        } else {
            launchAlarmActivity(token, initialTrigger, runtimeConfig.fullScreenEnabled)
        }

        return START_NOT_STICKY
    }

    private fun promote(token: AlarmToken, trigger: TriggerSnapshot?, fullScreenEnabled: Boolean) {
        createNotificationChannel(this)

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

        val contentIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("cameraalarm://alarm_view/${token.value}")
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
        }
        val contentPending = PendingIntent.getActivity(
            this,
            (token.value + "_view").hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setContentIntent(contentPending)

        val pendingFullScreen = createFullScreenPendingIntent(token, trigger, fullScreenEnabled)
        if (pendingFullScreen != null) {
            builder.setFullScreenIntent(pendingFullScreen, true)
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createFullScreenPendingIntent(
        token: AlarmToken,
        trigger: TriggerSnapshot?,
        fullScreenEnabled: Boolean
    ): PendingIntent? {
        val canFullScreen = app.container.readiness.canUseFullScreenIntent()
        if (!canFullScreen || !fullScreenEnabled) return null

        val fullScreenIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("cameraalarm://alarm_full/${token.value}")
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
        }

        return PendingIntent.getActivity(
            this,
            (token.value + "_full").hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launchAlarmActivity(token: AlarmToken, trigger: TriggerSnapshot?, fullScreenEnabled: Boolean) {
        val pending = createFullScreenPendingIntent(token, trigger, fullScreenEnabled)
        if (pending != null) {
            Log.i("CameraAlarm", "FULLSCREEN_INTENT_SENT: token=${token.value}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val launchOpts = android.app.ActivityOptions.makeBasic().apply {
                        val mode = if (Build.VERSION.SDK_INT >= 36) {
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        } else {
                            @Suppress("DEPRECATION")
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                        setPendingIntentBackgroundActivityStartMode(mode)
                    }
                    pending.send(this, 0, null, null, null, null, launchOpts.toBundle())
                } else {
                    pending.send()
                }
            } catch (e: Exception) {
                if (com.personal.cameraalarm.BuildConfig.DEBUG) {
                    Log.d("CameraAlarm", "pending.send skipped: ${e.message}")
                }
            }
        }

        // Direct startActivity launch: ensures full-screen display when screen is on / unlocked
        val canFullScreen = app.container.readiness.canUseFullScreenIntent()
        if (canFullScreen && fullScreenEnabled) {
            try {
                val directIntent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    data = android.net.Uri.parse("cameraalarm://alarm_full/${token.value}")
                    putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
                    putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
                }
                startActivity(directIntent)
                Log.i("CameraAlarm", "Direct startActivity called for token=${token.value}")
            } catch (e2: Exception) {
                if (com.personal.cameraalarm.BuildConfig.DEBUG) {
                    Log.d("CameraAlarm", "Direct activity start fallback skipped: ${e2.message}")
                }
            }
        }
    }

    private fun launchAcceptedTestActivity(token: AlarmToken, trigger: TriggerSnapshot?) {
        val intent = Intent(this, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("cameraalarm://alarm_test/${token.value}")
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_SOURCE, trigger?.sourcePackage)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, trigger?.title)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, trigger?.textPreview)
            putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, trigger?.receivedAtEpochMs ?: System.currentTimeMillis())
        }
        try {
            startActivity(intent)
        } catch (error: RuntimeException) {
            if (com.personal.cameraalarm.BuildConfig.DEBUG) {
                Log.d("CameraAlarm", "Test alarm activity launch skipped: ${error.message}")
            }
        }
    }
    private fun recordError(token: AlarmToken?, error: String) {
        Log.e("CameraAlarm", "alarm runtime error token=${token?.value}: $error")
        app.container.runtimeDiagnostics.record(error)
    }
    override fun onDestroy() {
        destroyed = true
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

        fun createNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channelName = context.getString(com.personal.cameraalarm.R.string.notification_channel_alarm)
            val channelDesc = context.getString(com.personal.cameraalarm.R.string.notification_channel_alarm_desc)
            val channel = NotificationChannel(CHANNEL, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = channelDesc
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
