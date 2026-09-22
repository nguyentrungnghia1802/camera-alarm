package com.personal.cameraalarm.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.personal.cameraalarm.app.AppContainer

class TestAlarmController(private val container: AppContainer) {
    fun canStart(): Boolean = container.coordinator.initialized.value && AlarmRuntimeOwnership.canStartTest(
        container.coordinator.state.value,
        container.testAlarmToken.value
    )

    fun start(context: Context): Result<Unit> = runCatching {
        check(canStart()) { "A production alarm is pending or active." }
        val testToken = AlarmToken("test-${System.currentTimeMillis()}")
        val intent = Intent(context, CameraAlarmService::class.java).apply {
            action = CameraAlarmService.ACTION_START
            putExtra(AlarmReceiver.EXTRA_TOKEN, testToken.value)
            putExtra(CameraAlarmService.EXTRA_IS_TEST, true)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val token = AlarmRuntimeOwnership.stopToken(
            container.coordinator.state.value,
            container.testAlarmToken.value
        ) ?: return
        val intent = StopAlarmReceiver.intent(context, token.value)
        context.sendBroadcast(intent)
    }
}
