package com.personal.cameraalarm

import androidx.datastore.preferences.core.booleanPreferencesKey
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
        } finally {
            scope.cancel()
            file.delete()
        }
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
