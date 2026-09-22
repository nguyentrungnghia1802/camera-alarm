package com.personal.cameraalarm

import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.alarm.AlarmState
import com.personal.cameraalarm.schedule.ScheduleMode
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Explicit fixture for adb reboot experiments, skipped by the normal suite. Does not fake a boot. */
class RebootScenarioPreparation {
    @Test fun prepare() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("prepareReboot") == "true")
        val enabled = args.getString("monitoring") != "false"
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CameraAlarmApp
        val container = app.container
        container.coordinator.reconcile()
        val state = container.coordinator.snapshot()
        if (state is AlarmState.Pending) container.coordinator.onStopRequested(state.trigger.alarmToken)
        if (state is AlarmState.Ringing) container.coordinator.onStopRequested(state.trigger.alarmToken)
        container.coordinator.resetCooldown()
        container.settingsRepository.setMonitoringEnabled(enabled)
        container.settingsRepository.setAlarmDelayMs(3_000)
        container.settingsRepository.setCooldownMs(0)
        container.settingsRepository.setFullScreenEnabled(false)
        container.settingsRepository.setScheduleMode(ScheduleMode.ALWAYS_ACTIVE)
        container.settingsRepository.setSourceApp("com.android.shell", "ADB notification fixture")
        container.ruleRepository.saveRule(TriggerRule("reboot-e2e", "Reboot E2E", true,
            "com.android.shell", MatchMode.CONTAINS_ANY, listOf("reboot probe"), 1, System.currentTimeMillis()))
        withTimeout(10_000) {
            while (container.triggerConfiguration.monitoringEnabled != enabled ||
                container.triggerConfiguration.rules.none { it.id == "reboot-e2e" } ||
                container.alarmPolicy.delayMs != 3_000L) delay(25)
        }
        assertEquals(enabled, container.settingsRepository.current().monitoringEnabled)
    }
}
