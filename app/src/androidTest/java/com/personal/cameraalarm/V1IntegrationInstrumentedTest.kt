package com.personal.cameraalarm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.cameraalarm.alarm.AlarmState
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.DataStoreAlarmStateStore
import com.personal.cameraalarm.alarm.TriggerSnapshot
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.data.AppDatabase
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.rule.TriggerRuleEntity
import com.personal.cameraalarm.data.settings.SettingsRepository
import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerConfiguration
import com.personal.cameraalarm.trigger.TriggerDecision
import com.personal.cameraalarm.trigger.TriggerRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V1IntegrationInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun settingsPersistAcrossRepositoryRecreation() = runBlocking {
        val first = SettingsRepository(context)
        first.setMonitoringEnabled(true)
        first.setSourceApp("com.camera.persisted", "Persisted Camera")
        first.setAlarmDelayMs(5_000)
        first.setCooldownMs(30_000)
        first.setVibrationEnabled(false)
        first.setFullScreenEnabled(true)

        val loaded = SettingsRepository(context).current()
        assertTrue(loaded.monitoringEnabled)
        assertEquals("com.camera.persisted", loaded.sourcePackage)
        assertEquals("Persisted Camera", loaded.sourceLabel)
        assertEquals(5_000, loaded.alarmDelayMs)
        assertEquals(30_000, loaded.cooldownMs)
        assertFalse(loaded.vibrationEnabled)
        assertTrue(loaded.fullScreenEnabled)

        first.setMonitoringEnabled(false)
        first.setSourceApp(null, null)
        first.setAlarmDelayMs(1_000)
        first.setCooldownMs(10_000)
        first.setVibrationEnabled(true)
        first.setFullScreenEnabled(false)
    }

    @Test
    fun roomRulesAndHistoryPersistAcrossDatabaseReopen() = runBlocking {
        val databaseName = "v1-persistence-instrumented.db"
        context.deleteDatabase(databaseName)
        try {
            var database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            database.triggerRuleDao().insert(
                TriggerRuleEntity(
                    id = "persisted-rule",
                    name = "Persisted rule",
                    enabled = true,
                    sourcePackage = "com.camera.persisted",
                    matchMode = "CONTAINS_ANY",
                    keywordsJson = "[\"person\"]",
                    priority = 2,
                    createdAtEpochMs = 1_000,
                    updatedAtEpochMs = 2_000
                )
            )
            database.alertEventDao().insert(
                AlertEventEntity(
                    createdAtEpochMs = 3_000,
                    sourcePackage = "com.camera.persisted",
                    notificationKey = "persisted-event",
                    title = "Person detected",
                    textPreview = "Door",
                    normalizedHash = null,
                    decision = "SCHEDULED",
                    ruleId = "persisted-rule",
                    alarmToken = "persisted-token",
                    details = null
                )
            )
            database.close()

            database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            assertEquals("Persisted rule", database.triggerRuleDao().getById("persisted-rule")?.name)
            assertEquals("persisted-event", database.alertEventDao().observeAll().first().single().notificationKey)
            database.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun pendingStatePersistsButOrphanRingingStateIsCleared() = runBlocking {
        val token = AlarmToken("instrumented-pending")
        val trigger = TriggerSnapshot(token, "com.camera.persisted", "key", "rule", "Person", "Door", 1_000)
        val first = DataStoreAlarmStateStore(context)
        first.write(AlarmState.Pending(trigger, 6_000))

        val recreated = DataStoreAlarmStateStore(context)
        assertTrue(recreated.read() is AlarmState.Pending)

        first.write(AlarmState.Ringing(trigger, 6_000))
        assertEquals(AlarmState.Idle, recreated.read())
        first.write(AlarmState.Idle)
    }

    @Test
    fun persistedSettingsAndRulesDrivePipelineWhileMonitoringOffSuppresses() = runBlocking {
        val app = context.applicationContext as CameraAlarmApp
        val container = app.container
        val ruleId = "instrumented-runtime-rule"
        try {
            container.stateStore.write(AlarmState.Idle)
            container.settingsRepository.setSourceApp("com.camera.instrumented", "Instrumented Camera")
            container.settingsRepository.setAlarmDelayMs(5_000)
            container.settingsRepository.setMonitoringEnabled(true)
            container.ruleRepository.saveRule(
                TriggerRule(ruleId, "Person", true, "com.camera.instrumented", MatchMode.CONTAINS_ANY, listOf("person detected"), 1, 1_000)
            )
            awaitConfiguration {
                it.monitoringEnabled && it.sourcePackage == "com.camera.instrumented" && it.rules.any { rule -> rule.id == ruleId }
            }

            assertEquals(TriggerDecision.SCHEDULED, container.pipeline.process(incoming("integration-scheduled")))
            val pending = container.stateStore.read() as AlarmState.Pending
            container.coordinator.onStopRequested(pending.trigger.alarmToken)
            val delayFromNotification = pending.scheduledAtEpochMs - pending.trigger.receivedAtEpochMs
            assertTrue("Exact alarm must not be scheduled early", delayFromNotification >= 5_000)
            assertTrue("Pipeline overhead should stay bounded", delayFromNotification < 5_500)

            container.settingsRepository.setMonitoringEnabled(false)
            awaitConfiguration { !it.monitoringEnabled }
            assertEquals(TriggerDecision.IGNORED_MONITORING_OFF, container.pipeline.process(incoming("integration-disabled")))
        } finally {
            val state = container.stateStore.read()
            if (state is AlarmState.Pending) container.coordinator.onStopRequested(state.trigger.alarmToken)
            container.ruleRepository.deleteRule(ruleId)
            container.settingsRepository.setMonitoringEnabled(false)
            container.settingsRepository.setSourceApp(null, null)
            container.settingsRepository.setAlarmDelayMs(1_000)
            container.stateStore.write(AlarmState.Idle)
        }
    }

    private fun incoming(key: String) = IncomingNotification(
        key = key,
        packageName = "com.camera.instrumented",
        notificationId = 1,
        tag = null,
        postTimeEpochMs = System.currentTimeMillis(),
        title = "Person detected",
        text = "At the front door",
        bigText = null,
        textLines = emptyList(),
        subText = null
    )

    private suspend fun awaitConfiguration(predicate: (TriggerConfiguration) -> Boolean) {
        val app = context.applicationContext as CameraAlarmApp
        withTimeout(5_000) {
            while (!predicate(app.container.triggerConfiguration)) delay(25)
        }
    }
}
