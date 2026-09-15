package com.personal.cameraalarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.schedule.ActiveTimeRange
import com.personal.cameraalarm.schedule.ScheduleMode
import com.personal.cameraalarm.schedule.ScheduleSerializer
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
                fullScreenEnabled = preferences[FULL_SCREEN_ENABLED] ?: false,
                alarmSoundKey = preferences[ALARM_SOUND_KEY] ?: AlarmSoundCatalog.DEFAULT_KEY,
                scheduleMode = preferences[SCHEDULE_MODE]?.let {
                    runCatching { ScheduleMode.valueOf(it) }.getOrNull()
                } ?: ScheduleMode.ALWAYS_ACTIVE,
                scheduleRanges = ScheduleSerializer.deserialize(preferences[SCHEDULE_RANGES]),
                language = preferences[LANGUAGE] ?: "vi"
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

    suspend fun setAlarmSound(soundKey: String) {
        dataStore.edit { it[ALARM_SOUND_KEY] = soundKey }
    }

    suspend fun setScheduleMode(mode: ScheduleMode) {
        dataStore.edit { it[SCHEDULE_MODE] = mode.name }
    }

    suspend fun setScheduleRanges(ranges: List<ActiveTimeRange>) {
        dataStore.edit { it[SCHEDULE_RANGES] = ScheduleSerializer.serialize(ranges) }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { it[LANGUAGE] = language }
    }

    suspend fun updateAll(newSettings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[MONITORING_ENABLED] = newSettings.monitoringEnabled
            if (newSettings.sourcePackage != null) prefs[SOURCE_PACKAGE] = newSettings.sourcePackage else prefs.remove(SOURCE_PACKAGE)
            if (newSettings.sourceLabel != null) prefs[SOURCE_LABEL] = newSettings.sourceLabel else prefs.remove(SOURCE_LABEL)
            prefs[ALARM_DELAY_MS] = newSettings.alarmDelayMs
            prefs[COOLDOWN_MS] = newSettings.cooldownMs
            prefs[VIBRATION_ENABLED] = newSettings.vibrationEnabled
            prefs[FULL_SCREEN_ENABLED] = newSettings.fullScreenEnabled
            prefs[ALARM_SOUND_KEY] = newSettings.alarmSoundKey
            prefs[SCHEDULE_MODE] = newSettings.scheduleMode.name
            prefs[SCHEDULE_RANGES] = ScheduleSerializer.serialize(newSettings.scheduleRanges)
            prefs[LANGUAGE] = newSettings.language
        }
    }

    companion object {
        private val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        private val SOURCE_PACKAGE = stringPreferencesKey("source_package")
        private val SOURCE_LABEL = stringPreferencesKey("source_label")
        private val ALARM_DELAY_MS = longPreferencesKey("alarm_delay_ms")
        private val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val FULL_SCREEN_ENABLED = booleanPreferencesKey("full_screen_enabled")
        private val ALARM_SOUND_KEY = stringPreferencesKey("alarm_sound_key")
        private val SCHEDULE_MODE = stringPreferencesKey("schedule_mode")
        private val SCHEDULE_RANGES = stringPreferencesKey("schedule_ranges")
        private val LANGUAGE = stringPreferencesKey("language")
    }
}
