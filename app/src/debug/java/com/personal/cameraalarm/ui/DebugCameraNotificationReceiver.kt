package com.personal.cameraalarm.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerConfiguration
import com.personal.cameraalarm.trigger.TriggerRule

/** Posts a real Android notification to exercise NotificationListenerService. */
class DebugCameraNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CameraAlarmApp
        val source = context.packageName
        app.container.triggerConfiguration = TriggerConfiguration(true, source,
            listOf(TriggerRule("debug-notification", "Debug camera", true, source, MatchMode.CONTAINS_ANY, listOf("human detected"), 0, 0)))
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Debug camera source", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Human detected")
            .setContentText("Motion at door")
            .build()
        manager.notify(intent.getIntExtra("id", 1), notification)
    }
    companion object { private const val CHANNEL = "debug_camera_source" }
}
