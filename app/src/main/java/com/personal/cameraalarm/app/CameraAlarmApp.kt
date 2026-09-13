package com.personal.cameraalarm.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CameraAlarmApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val container by lazy { AppContainer(this) }
}
