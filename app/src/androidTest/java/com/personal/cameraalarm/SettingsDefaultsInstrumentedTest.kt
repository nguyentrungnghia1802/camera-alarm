package com.personal.cameraalarm

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.data.settings.AppSettings
import com.personal.cameraalarm.data.settings.SettingsDefaults
import com.personal.cameraalarm.data.settings.SettingsRepository
import com.personal.cameraalarm.schedule.ScheduleMode
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDefaultsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun freshStoreUsesOfficialDefaultProfile() = withRepository { repository ->
        val settings = repository.current()

        assertEquals(AlarmSoundCatalog.OFFICIAL_PROFILE_DEFAULT_KEY, settings.alarmSoundKey)
        assertEquals(SettingsDefaults.COOLDOWN_MS, settings.cooldownMs)
        assertEquals(ScheduleMode.CUSTOM, settings.scheduleMode)
        assertEquals(SettingsDefaults.OVERNIGHT_START_MINUTES, settings.scheduleRanges.single().startMinutes)
        assertEquals(SettingsDefaults.OVERNIGHT_END_MINUTES, settings.scheduleRanges.single().endMinutes)
        assertTrue(settings.fullScreenEnabled)
    }

    @Test
    fun existingStoreWithoutVersionPreservesOldEffectiveDefaults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = context.preferencesDataStoreFile("settings-existing-${UUID.randomUUID()}")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            store.edit { it[booleanPreferencesKey("monitoring_enabled")] = true }
            val settings = SettingsRepository(context, store).current()

            assertTrue(settings.monitoringEnabled)
            assertEquals(SettingsDefaults.LEGACY_COOLDOWN_MS, settings.cooldownMs)
            assertEquals(AlarmSoundCatalog.DEFAULT_KEY, settings.alarmSoundKey)
            assertEquals(ScheduleMode.ALWAYS_ACTIVE, settings.scheduleMode)
            assertTrue(settings.scheduleRanges.isEmpty())
            assertFalse(settings.fullScreenEnabled)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun resetPersistsOfficialDefaultsAcrossRepositoryRecreation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = context.preferencesDataStoreFile("settings-reset-${UUID.randomUUID()}")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val repository = SettingsRepository(context, store)
            repository.updateAll(
                AppSettings(
                    monitoringEnabled = true,
                    sourcePackage = "legacy.camera",
                    sourceLabel = "Legacy Camera",
                    cooldownMs = 12_345,
                    fullScreenEnabled = true,
                    language = "en"
                )
            )

            repository.resetToDefaults()
            val restored = SettingsRepository(context, store).current()

            assertEquals(SettingsDefaults.official(), restored)
            assertFalse(restored.monitoringEnabled)
            assertNull(restored.sourcePackage)
            assertEquals("vi", restored.language)
            assertTrue(restored.fullScreenEnabled)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun existingV2StorePreservesFalseWhenUpgradedToV3() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = context.preferencesDataStoreFile("settings-v2-${UUID.randomUUID()}")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            store.edit {
                it[intPreferencesKey("settings_profile_version")] = 2
                it[booleanPreferencesKey("monitoring_enabled")] = true
            }
            val settings = SettingsRepository(context, store).current()

            assertTrue(settings.monitoringEnabled)
            // fullScreenEnabled was false in V2 and wasn't explicitly set; migration must preserve false
            assertFalse(settings.fullScreenEnabled)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun savingStaleSettingsDraftDoesNotDisableMonitoring() = withRepository { repository ->
        val staleDraft = repository.current().copy(
            monitoringEnabled = false,
            cooldownMs = 45_000
        )
        repository.setMonitoringEnabled(true)

        repository.updateEditableSettings(staleDraft)

        val restored = repository.current()
        assertTrue(restored.monitoringEnabled)
        assertEquals(45_000, restored.cooldownMs)
    }

    private fun withRepository(block: suspend (SettingsRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = context.preferencesDataStoreFile("settings-fresh-${UUID.randomUUID()}")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            block(SettingsRepository(context, store))
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
