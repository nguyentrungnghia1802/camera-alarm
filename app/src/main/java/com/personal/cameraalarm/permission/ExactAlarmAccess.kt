package com.personal.cameraalarm.permission

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class ExactAlarmAccess(private val context: Context) {
    fun isGranted(): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun requestIntent(): Intent? = if (Build.VERSION.SDK_INT >= 31)
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    else null
}
