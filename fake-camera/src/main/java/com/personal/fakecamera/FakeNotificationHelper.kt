package com.personal.fakecamera

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

data class AlertPayload(
    val title: String,
    val text: String
) {
    companion object {
        val HUMAN_DETECTED = AlertPayload(
            title = "Human detected",
            text = "Front Door Camera detected a person"
        )
        val MOTION_DETECTED = AlertPayload(
            title = "Motion detected",
            text = "Front Door Camera detected movement"
        )
        val CAMERA_OFFLINE = AlertPayload(
            title = "Camera offline",
            text = "Front Door Camera connection lost"
        )
    }
}

data class SentAlertLog(
    val id: Int,
    val title: String,
    val text: String,
    val timestampEpochMs: Long
)

class FakeNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "fake_camera_alerts"
        const val CHANNEL_NAME = "Camera Alerts"
        const val CHANNEL_DESC = "Simulated alerts from Front Door Camera"
        private val idCounter = AtomicInteger(1000)

        fun nextNotificationId(): Int = idCounter.incrementAndGet()
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postNotification(
        payload: AlertPayload,
        customId: Int? = null
    ): SentAlertLog {
        val notificationId = customId ?: nextNotificationId()
        val now = System.currentTimeMillis()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera_alert)
            .setContentTitle(payload.title)
            .setContentText(payload.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setWhen(now)
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)

        return SentAlertLog(
            id = notificationId,
            title = payload.title,
            text = payload.text,
            timestampEpochMs = now
        )
    }
}
