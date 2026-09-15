package com.personal.cameraalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.cameraalarm.ui.MainActivity

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult {
        if (!canScheduleExactAlarms()) return ScheduleResult.ExactAlarmPermissionMissing
        return try {
            val intent = intent().putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Primary: Use setAlarmClock for critical alarm delivery that wakes CPU and bypasses Doze throttling
            try {
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtEpochMs, showIntent)
                manager.setAlarmClock(clockInfo, pending)
            } catch (_: SecurityException) {
                // Fallback if setAlarmClock restricted by platform policy
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pending)
            }

            android.util.Log.i("CameraAlarm", "EXACT_ALARM_SCHEDULED: token=${token.value} triggerAtEpochMs=$triggerAtEpochMs")
            ScheduleResult.Scheduled
        } catch (e: SecurityException) {
            ScheduleResult.ExactAlarmPermissionMissing
        } catch (e: RuntimeException) {
            ScheduleResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun cancel(token: AlarmToken) {
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun intent() = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)

    companion object {
        private const val REQUEST_CODE = 1
    }
}
