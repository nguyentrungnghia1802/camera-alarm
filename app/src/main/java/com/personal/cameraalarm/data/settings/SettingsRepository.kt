package com.personal.cameraalarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appSettingsDataStore by preferencesDataStore(name = "camera_alarm_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.appSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            AppSettings(
                monitoringEnabled = preferences[MONITORING_ENABLED] ?: false,
                sourcePackage = preferences[SOURCE_PACKAGE],
                sourceLabel = preferences[SOURCE_LABEL],
                alarmDelayMs = preferences[ALARM_DELAY_MS] ?: 1000L,
                cooldownMs = preferences[COOLDOWN_MS] ?: 10000L,
                vibrationEnabled = preferences[VIBRATION_ENABLED] ?: true,
                fullScreenEnabled = preferences[FULL_SCREEN_ENABLED] ?: false
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[MONITORING_ENABLED] = enabled }
    }

    suspend fun setSourceApp(packageName: String?, label: String?) {
        dataStore.edit {
            if (packageName != null) it[SOURCE_PACKAGE] = packageName else it.remove(SOURCE_PACKAGE)
            if (label != null) it[SOURCE_LABEL] = label else it.remove(SOURCE_LABEL)
        }
    }

    suspend fun setAlarmDelayMs(delayMs: Long) {
        dataStore.edit { it[ALARM_DELAY_MS] = delayMs }
    }

    suspend fun setCooldownMs(cooldownMs: Long) {
        dataStore.edit { it[COOLDOWN_MS] = cooldownMs }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATION_ENABLED] = enabled }
    }

    suspend fun setFullScreenEnabled(enabled: Boolean) {
        dataStore.edit { it[FULL_SCREEN_ENABLED] = enabled }
    }

    companion object {
        private val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        private val SOURCE_PACKAGE = stringPreferencesKey("source_package")
        private val SOURCE_LABEL = stringPreferencesKey("source_label")
        private val ALARM_DELAY_MS = longPreferencesKey("alarm_delay_ms")
        private val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val FULL_SCREEN_ENABLED = booleanPreferencesKey("full_screen_enabled")
    }
}
