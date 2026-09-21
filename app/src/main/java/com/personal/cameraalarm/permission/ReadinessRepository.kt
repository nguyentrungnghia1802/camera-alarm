package com.personal.cameraalarm.permission

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.personal.cameraalarm.notification.CameraNotificationListener
import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.notification.ListenerStatus
import com.personal.cameraalarm.trigger.NotificationNormalizer
import com.personal.cameraalarm.trigger.TriggerConfiguration

data class ReadinessState(
    val notificationAccessGranted: Boolean,
    val listenerConnected: Boolean,
    val exactAlarmGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val sourceConfigured: Boolean,
    val ruleConfigured: Boolean,
    val alarmVolumeNonZero: Boolean,
    val listenerStatus: ListenerStatus = if (listenerConnected) ListenerStatus.CONNECTED else ListenerStatus.DISCONNECTED
) {
    val blockingReady get() = notificationAccessGranted && exactAlarmGranted && sourceConfigured && ruleConfigured
    val readyForMonitoring get() = blockingReady && listenerConnected && postNotificationsGranted && alarmVolumeNonZero
}

class ReadinessRepository(private val context: Context, private val exact: ExactAlarmAccess,
                          private val listener: ListenerConnectionState, private val configuration: () -> TriggerConfiguration) {
    fun volumeStatus(): AlarmVolumeStatus {
        val audio = context.getSystemService(AudioManager::class.java)
        val minimum = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_ALARM) else 0
        return AlarmVolumeStatus(audio.getStreamVolume(AudioManager.STREAM_ALARM), minimum,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
    }
    fun snapshot(): ReadinessState {
        val config = configuration()
        val enabledValidRules = config.rules.filter { rule ->
            rule.enabled &&
                rule.sourcePackage.isNotBlank() &&
                rule.keywords.any { word -> NotificationNormalizer.normalize(word).isNotEmpty() }
        }
        return ReadinessState(
            notificationAccessGranted = notificationAccessGranted(),
            listenerConnected = listener.status.value == ListenerStatus.CONNECTED,
            exactAlarmGranted = exact.isGranted(),
            postNotificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            sourceConfigured = enabledValidRules.any { it.sourcePackage.isNotBlank() },
            ruleConfigured = enabledValidRules.isNotEmpty(),
            alarmVolumeNonZero = volumeStatus().isNonZero,
            listenerStatus = listener.status.value
        )
    }
    private fun notificationAccessGranted(): Boolean {
        val component = ComponentName(context, CameraNotificationListener::class.java)
        return if (Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }

    fun notificationAccessSettingsIntent(): android.content.Intent {
        return if (Build.VERSION.SDK_INT >= 30) {
            val component = ComponentName(context, CameraNotificationListener::class.java)
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    fun exactAlarmSettingsIntent(): android.content.Intent? {
        return exact.requestIntent()?.apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
    }

    fun appNotificationSettingsIntent(): android.content.Intent {
        return android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }
    }

    fun fullScreenIntentSettingsIntent(): android.content.Intent? {
        return if (Build.VERSION.SDK_INT >= 34) {
            android.content.Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}")).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            null
        }
    }
}
