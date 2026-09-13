package com.personal.cameraalarm.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class CameraNotificationListener : NotificationListenerService() {
    private val app get() = application as CameraAlarmApp
    override fun onListenerConnected() { super.onListenerConnected(); app.container.listenerConnection.connected(); if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "listener connected") }
    override fun onListenerDisconnected() { app.container.listenerConnection.disconnected(); if (com.personal.cameraalarm.BuildConfig.DEBUG) Log.d("CameraAlarm", "listener disconnected"); super.onListenerDisconnected() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (app.container.listenerConnection.status.value != ListenerStatus.CONNECTED || sbn == null) return
        val incoming = NotificationExtractor.from(sbn)
        app.scope.launch { app.container.pipeline.process(incoming) }
    }
    override fun onDestroy() { app.container.listenerConnection.disconnected(); super.onDestroy() }
}
