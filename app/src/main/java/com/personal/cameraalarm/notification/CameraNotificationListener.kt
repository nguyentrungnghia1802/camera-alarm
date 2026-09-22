package com.personal.cameraalarm.notification

import android.content.ComponentName
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class CameraNotificationListener : NotificationListenerService() {
    private val app get() = application as CameraAlarmApp

    override fun onListenerConnected() {
        super.onListenerConnected()
        app.container.listenerConnection.connected()
        if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "listener connected")
    }

    override fun onListenerDisconnected() {
        app.container.listenerConnection.disconnected()
        if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "listener disconnected")
        super.onListenerDisconnected()
        app.container.recoveryJobs.boot("LISTENER_DISCONNECTED")
        // Auto-heal / reconnect if system unexpectedly unbinds
        try {
            requestRebind(ComponentName(this, CameraNotificationListener::class.java))
        } catch (_: Throwable) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (app.container.listenerConnection.status.value != ListenerStatus.CONNECTED) {
            app.container.listenerConnection.connected()
        }
        Log.i("CameraAlarm", "NOTIFICATION_RECEIVED: pkg=${sbn.packageName} id=${sbn.id}")
        val incoming = NotificationExtractor.from(sbn)
        com.personal.cameraalarm.alarm.AlarmTrace.record("NOTIFICATION_RECEIVED",
            com.personal.cameraalarm.alarm.AlarmToken(incoming.traceToken),
            details = "package=${incoming.packageName} key=${incoming.key}")

        val pm = getSystemService(PowerManager::class.java)
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraAlarm:NotificationPostedWakeLock")
        wakeLock?.acquire(10_000)

        app.scope.launch {
            try {
                app.container.pipeline.process(incoming)
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onDestroy() {
        app.container.listenerConnection.disconnected()
        super.onDestroy()
    }
}
