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
    val alarmVolumeNonZero: Boolean
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
        val source = config.sourcePackage?.takeIf(String::isNotBlank)
        return ReadinessState(
            notificationAccessGranted = notificationAccessGranted(),
            listenerConnected = listener.status.value == ListenerStatus.CONNECTED,
            exactAlarmGranted = exact.isGranted(),
            postNotificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            sourceConfigured = source != null,
            ruleConfigured = source != null && config.rules.any { it.enabled && it.sourcePackage == source && it.keywords.any { word -> NotificationNormalizer.normalize(word).isNotEmpty() } },
            alarmVolumeNonZero = volumeStatus().isNonZero
        )
    }
    private fun notificationAccessGranted(): Boolean {
        val component = ComponentName(context, CameraNotificationListener::class.java)
        return if (Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }
}
