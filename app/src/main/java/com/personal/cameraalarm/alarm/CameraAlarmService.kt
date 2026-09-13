package com.personal.cameraalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** P1.3 foreground bridge; P1.4 owns continuous runtime and STOP. */
class CameraAlarmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Camera alarms", NotificationManager.IMPORTANCE_HIGH))
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Camera Alert").setContentText("Camera notification detected")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(1, notification)
        return START_NOT_STICKY
    }
    companion object { const val ACTION_START = "com.personal.cameraalarm.action.START_ALARM"; const val CHANNEL = "alarm_runtime" }
}
