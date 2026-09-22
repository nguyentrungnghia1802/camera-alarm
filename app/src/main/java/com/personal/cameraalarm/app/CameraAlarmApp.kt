package com.personal.cameraalarm.app

import android.app.Application
import com.personal.cameraalarm.alarm.CameraAlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CameraAlarmApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize container so settings and rules are loaded into memory immediately
        container
        container.startRecovery()
        // Ensure alarm notification channel exists
        CameraAlarmService.createNotificationChannel(this)
    }
}
