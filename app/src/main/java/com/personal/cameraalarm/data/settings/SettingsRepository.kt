package com.personal.cameraalarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.schedule.ActiveTimeRange
import com.personal.cameraalarm.schedule.ScheduleMode
import com.personal.cameraalarm.schedule.ScheduleSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException

private val Context.appSettingsDataStore by preferencesDataStore(name = "camera_alarm_settings")

class SettingsRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.appSettingsDataStore
) {

    val settings: Flow<AppSettings> = dataStore.data
        .onStart { migrateDefaultProfileIfNeeded() }
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            AppSettings(
                monitoringEnabled = preferences[MONITORING_ENABLED] ?: false,
                sourcePackage = preferences[SOURCE_PACKAGE],
                sourceLabel = preferences[SOURCE_LABEL],
                alarmDelayMs = preferences[ALARM_DELAY_MS] ?: 1000L,
                cooldownMs = preferences[COOLDOWN_MS] ?: SettingsDefaults.COOLDOWN_MS,
                vibrationEnabled = preferences[VIBRATION_ENABLED] ?: true,
                fullScreenEnabled = preferences[FULL_SCREEN_ENABLED] ?: true,
                alarmSoundKey = preferences[ALARM_SOUND_KEY] ?: AlarmSoundCatalog.OFFICIAL_PROFILE_DEFAULT_KEY,
                scheduleMode = preferences[SCHEDULE_MODE]?.let {
                    runCatching { ScheduleMode.valueOf(it) }.getOrNull()
                } ?: ScheduleMode.CUSTOM,
                scheduleRanges = preferences[SCHEDULE_RANGES]?.let(ScheduleSerializer::deserialize)
                    ?: SettingsDefaults.scheduleRanges(),
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
            writeSettings(prefs, newSettings, includeMonitoring = true)
        }
    }

    /**
     * Persists fields owned by the Settings screen without changing whether
     * camera monitoring is enabled. Monitoring is controlled independently
     * from the dashboard and a stale settings draft must never disable it.
     */
    suspend fun updateEditableSettings(newSettings: AppSettings) {
        dataStore.edit { prefs ->
            writeSettings(prefs, newSettings, includeMonitoring = false)
        }
    }

    suspend fun resetToDefaults(): AppSettings {
        val defaults = SettingsDefaults.official()
        updateAll(defaults)
        return defaults
    }

    private suspend fun migrateDefaultProfileIfNeeded() {
        dataStore.edit { prefs ->
            val version = prefs[SETTINGS_PROFILE_VERSION] ?: 0
            if (version >= CURRENT_PROFILE_VERSION) return@edit
            val isExistingInstall = prefs.asMap().isNotEmpty()
            if (isExistingInstall) {
                // Preserve the old effective behavior when V1 never persisted these fields.
                if (prefs[COOLDOWN_MS] == null) prefs[COOLDOWN_MS] = SettingsDefaults.LEGACY_COOLDOWN_MS
                if (prefs[ALARM_SOUND_KEY] == null) prefs[ALARM_SOUND_KEY] = AlarmSoundCatalog.DEFAULT_KEY
                if (prefs[SCHEDULE_MODE] == null) prefs[SCHEDULE_MODE] = ScheduleMode.ALWAYS_ACTIVE.name
                if (prefs[SCHEDULE_RANGES] == null) prefs[SCHEDULE_RANGES] = ScheduleSerializer.serialize(emptyList())
                // In versions < 3, fullScreenEnabled defaulted to false.
                // For an existing install that had not explicitly set FULL_SCREEN_ENABLED,
                // preserve their old effective behavior (false) so update never overwrites user setting!
                if (prefs[FULL_SCREEN_ENABLED] == null) {
                    prefs[FULL_SCREEN_ENABLED] = false
                }
            } else {
                prefs[COOLDOWN_MS] = SettingsDefaults.COOLDOWN_MS
                prefs[ALARM_SOUND_KEY] = AlarmSoundCatalog.OFFICIAL_PROFILE_DEFAULT_KEY
                prefs[SCHEDULE_MODE] = ScheduleMode.CUSTOM.name
                prefs[SCHEDULE_RANGES] = ScheduleSerializer.serialize(SettingsDefaults.scheduleRanges())
                prefs[FULL_SCREEN_ENABLED] = true
            }
            prefs[SETTINGS_PROFILE_VERSION] = CURRENT_PROFILE_VERSION
        }
    }

    private fun writeSettings(
        prefs: MutablePreferences,
        settings: AppSettings,
        includeMonitoring: Boolean
    ) {
        if (includeMonitoring) prefs[MONITORING_ENABLED] = settings.monitoringEnabled
        if (settings.sourcePackage != null) prefs[SOURCE_PACKAGE] = settings.sourcePackage else prefs.remove(SOURCE_PACKAGE)
        if (settings.sourceLabel != null) prefs[SOURCE_LABEL] = settings.sourceLabel else prefs.remove(SOURCE_LABEL)
        prefs[ALARM_DELAY_MS] = settings.alarmDelayMs
        prefs[COOLDOWN_MS] = settings.cooldownMs
        prefs[VIBRATION_ENABLED] = settings.vibrationEnabled
        prefs[FULL_SCREEN_ENABLED] = settings.fullScreenEnabled
        prefs[ALARM_SOUND_KEY] = settings.alarmSoundKey
        prefs[SCHEDULE_MODE] = settings.scheduleMode.name
        prefs[SCHEDULE_RANGES] = ScheduleSerializer.serialize(settings.scheduleRanges)
        prefs[LANGUAGE] = settings.language
        prefs[SETTINGS_PROFILE_VERSION] = CURRENT_PROFILE_VERSION
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
        private val SETTINGS_PROFILE_VERSION = intPreferencesKey("settings_profile_version")
        private const val CURRENT_PROFILE_VERSION = 3
    }
}
