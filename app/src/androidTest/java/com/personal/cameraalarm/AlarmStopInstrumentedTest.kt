package com.personal.cameraalarm

import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.alarm.AlarmReceiver
import com.personal.cameraalarm.alarm.CameraAlarmService
import com.personal.cameraalarm.ui.alarm.AlarmActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmStopInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun secondStartCannotReplaceStopActionForActiveAlarm() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permissionOutput = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "cmd appops set --uid ${context.packageName} SCHEDULE_EXACT_ALARM allow"
            )
        ).bufferedReader().use { it.readText() }
        assertEquals("Exact-alarm grant command failed", "", permissionOutput.trim())
        if (Build.VERSION.SDK_INT >= 33) {
            val notificationOutput = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
                )
            ).bufferedReader().use { it.readText() }
            assertEquals("Notification grant command failed", "", notificationOutput.trim())
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        fun start(token: String) {
            val intent = Intent(context, CameraAlarmService::class.java).apply {
                action = CameraAlarmService.ACTION_START
                putExtra(AlarmReceiver.EXTRA_TOKEN, token)
                putExtra(CameraAlarmService.EXTRA_IS_TEST, true)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(token: String) {
            context.startService(Intent(context, CameraAlarmService::class.java).apply {
                action = CameraAlarmService.ACTION_STOP
                putExtra(AlarmReceiver.EXTRA_TOKEN, token)
            })
        }

        try {
            start("test-first")
            withTimeout(5_000) { while (manager.activeNotifications.none { it.id == 1 }) delay(25) }
            withTimeout(10_000) {
                while (true) {
                    var resumed = false
                    instrumentation.runOnMainSync {
                        resumed = ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .any { it is AlarmActivity }
                    }
                    if (resumed) break
                    delay(25)
                }
            }
            start("test-second")
            instrumentation.waitForIdleSync()
            val stopLabel = context.getString(R.string.btn_stop_alarm)
            val stopAction = manager.activeNotifications
                .single { it.id == 1 }
                .notification
                .actions
                .single { it.title.toString() == stopLabel }
            stopAction.actionIntent.send()
            // A cold emulator can spend several seconds rendering the alarm
            // activity while the STOP broadcast is queued on the app main
            // thread. The product contract is eventual token-correct STOP, so
            // allow enough time for that cold-start path to drain.
            withTimeout(10_000) { while (manager.activeNotifications.any { it.id == 1 }) delay(25) }
            assertTrue(manager.activeNotifications.none { it.id == 1 })
        } finally {
            stop("test-first")
        }
    }
}
