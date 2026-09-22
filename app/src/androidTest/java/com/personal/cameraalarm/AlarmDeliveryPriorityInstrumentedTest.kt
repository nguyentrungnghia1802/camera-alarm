package com.personal.cameraalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Android 13 has separate ordered foreground/background broadcast queues. */
@SdkSuppress(minSdkVersion = 33, maxSdkVersion = 33)
class AlarmDeliveryPriorityInstrumentedTest {
    @Test fun retryDeliveryDoesNotWaitForUnrelatedBackgroundBroadcast() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as CameraAlarmApp).container
        assertTrue(container.exactAlarmAccess.isGranted())
        assertTrue(container.awaitConfigLoaded(10_000))
        val saved = container.settingsRepository.current()
        val coordinator = container.coordinator
        val initial = coordinator.snapshot()
        assertFalse(initial is AlarmState.Ringing)
        if (initial is AlarmState.Pending) coordinator.onStopRequested(initial.trigger.alarmToken)
        coordinator.resetCooldown()
        val entered = CompletableDeferred<BroadcastReceiver.PendingResult>()
        val action = "${context.packageName}.TEST_BACKGROUND_QUEUE"
        val blocker = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Keep only the OS background queue busy, never the main thread.
                entered.complete(goAsync())
                Log.i("DeliveryProbe", "BACKGROUND_QUEUE_HELD")
            }
        }
        ContextCompat.registerReceiver(context, blocker, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        var held: BroadcastReceiver.PendingResult? = null
        val original = AlarmToken("queue-probe-${System.nanoTime()}")
        try {
            context.sendOrderedBroadcast(Intent(action).setPackage(context.packageName), null)
            held = withTimeout(5_000) { entered.await() }
            container.settingsRepository.setAlarmDelayMs(1_000)
            withTimeout(5_000) { while (container.alarmPolicy.delayMs != 1_000L) delay(20) }
            assertEquals(AlarmOutcome.SCHEDULED, coordinator.onValidTrigger(
                TriggerSnapshot(original, "camera.test", "queue-key", "queue-rule", "Queue test", null, System.currentTimeMillis())))
            container.scheduler.cancel(original)
            // Same bound as the existing lost-delivery regression; no extra grace.
            withTimeout(25_000) { while (coordinator.state.value !is AlarmState.Ringing) delay(25) }
            val ringing = coordinator.snapshot() as AlarmState.Ringing
            assertNotEquals(original, ringing.trigger.alarmToken)
            assertFalse(coordinator.claimAlarm(original) { fail("Retired primary claimed retry") })
            assertEquals(ringing, coordinator.snapshot())
        } finally {
            held?.finish()
            Log.i("DeliveryProbe", "BACKGROUND_QUEUE_RELEASED")
            context.unregisterReceiver(blocker)
            val token = when (val state = coordinator.snapshot()) {
                is AlarmState.Pending -> state.trigger.alarmToken
                is AlarmState.Ringing -> state.trigger.alarmToken
                else -> original
            }
            coordinator.onStopRequested(token)
            context.startService(Intent(context, CameraAlarmService::class.java)
                .setAction(CameraAlarmService.ACTION_STOP).putExtra(AlarmReceiver.EXTRA_TOKEN, token.value))
            coordinator.resetCooldown()
            container.settingsRepository.setAlarmDelayMs(saved.alarmDelayMs)
        }
    }
}
