package com.personal.cameraalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult {
        if (!canScheduleExactAlarms()) return ScheduleResult.ExactAlarmPermissionMissing
        return try {
            val intent = intent().putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
            val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pending)
            ScheduleResult.Scheduled
        } catch (e: SecurityException) {
            ScheduleResult.ExactAlarmPermissionMissing
        } catch (e: RuntimeException) {
            ScheduleResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun cancel(token: AlarmToken) {
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { manager.cancel(pending); pending.cancel() }
    }

    private fun intent() = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
    companion object { private const val REQUEST_CODE = 1 }
}
