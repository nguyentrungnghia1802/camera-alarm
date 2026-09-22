package com.personal.cameraalarm

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PendingDeliveryInstrumentedTest {
    @Test fun lostOsDeliveryRetriesAutonomouslyAndLateCallbackCannotClaimReplacement() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as CameraAlarmApp).container
        assertTrue(container.exactAlarmAccess.isGranted())
        assertTrue(container.awaitConfigLoaded(10_000))
        val saved = container.settingsRepository.current()
        val coordinator = container.coordinator
        val initial = coordinator.snapshot()
        assertFalse("Test requires no active runtime", initial is AlarmState.Ringing)
        if (initial is AlarmState.Pending) coordinator.onStopRequested(initial.trigger.alarmToken)
        coordinator.resetCooldown()
        val original = AlarmToken("lost-delivery-${System.nanoTime()}")
        try {
            container.settingsRepository.setAlarmDelayMs(1_000)
            withTimeout(5_000) { while (container.alarmPolicy.delayMs != 1_000L) delay(20) }
            assertEquals(AlarmOutcome.SCHEDULED, coordinator.onValidTrigger(
                TriggerSnapshot(original, "camera.test", "lost-key", "rule", "Lost alarm test", null, System.currentTimeMillis())))
            // Remove just the primary alarm. The app watchdog and the OS repair job remain armed.
            container.scheduler.cancel(original)
            withTimeout(25_000) { while (coordinator.state.value !is AlarmState.Ringing) delay(25) }
            val ringing = coordinator.state.value as AlarmState.Ringing
            assertNotEquals(original, ringing.trigger.alarmToken)
            assertFalse(coordinator.claimAlarm(original) { fail("Old token reclaimed delivery") })
            assertEquals(ringing, coordinator.snapshot())
            assertEquals(saved.monitoringEnabled, container.settingsRepository.current().monitoringEnabled)
        } finally {
            val active = coordinator.snapshot()
            val token = when (active) {
                is AlarmState.Ringing -> active.trigger.alarmToken
                is AlarmState.Pending -> active.trigger.alarmToken
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
