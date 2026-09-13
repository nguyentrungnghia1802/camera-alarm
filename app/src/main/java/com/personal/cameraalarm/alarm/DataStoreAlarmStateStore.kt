package com.personal.cameraalarm.alarm

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.runtimeDataStore by preferencesDataStore(name = "alarm_runtime_state")

/** Stores a one-shot alarm snapshot atomically; rejects metadata from a prior boot. */
class DataStoreAlarmStateStore(private val context: Context) : AlarmStateStore {
    private val dataStore = context.runtimeDataStore
    private val processNonce = UUID.randomUUID().toString()
    private val boot get() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    override suspend fun read(): AlarmState {
        val p = dataStore.data.first()
        if (p[BOOT] != boot) { write(AlarmState.Idle); return AlarmState.Idle }
        return when (p[STATE]) {
            "pending" -> snapshot(p)?.let { AlarmState.Pending(it, p[AT] ?: return AlarmState.Idle) } ?: AlarmState.Idle
            "ringing" -> {
                if (p[OWNER_NONCE] != processNonce) { write(AlarmState.Idle); AlarmState.Idle }
                else snapshot(p)?.let { AlarmState.Ringing(it, p[AT] ?: return AlarmState.Idle) } ?: AlarmState.Idle
            }
            "cooldown" -> if (p[TOKEN] != null && p[AT] != null) AlarmState.Cooldown(p[AT]!!, AlarmToken(p[TOKEN]!!)) else AlarmState.Idle
            else -> AlarmState.Idle
        }
    }
    override suspend fun write(state: AlarmState) {
        dataStore.edit { p ->
            p.clear(); p[BOOT] = boot
            when (state) {
                AlarmState.Idle -> p[STATE] = "idle"
                is AlarmState.Pending -> { p[STATE] = "pending"; p[AT] = state.scheduledAtEpochMs; putSnapshot(p, state.trigger) }
                is AlarmState.Ringing -> { p[STATE] = "ringing"; p[AT] = state.startedAtEpochMs; p[OWNER_NONCE] = processNonce; putSnapshot(p, state.trigger) }
                is AlarmState.Cooldown -> { p[STATE] = "cooldown"; p[AT] = state.untilEpochMs; p[TOKEN] = state.lastAlarmToken.value }
            }
        }
    }
    private fun snapshot(p: Preferences): TriggerSnapshot? {
        val token = p[TOKEN]?.takeIf(String::isNotBlank) ?: return null
        val source = p[SOURCE]?.takeIf(String::isNotBlank) ?: return null
        val key = p[NOTIFICATION_KEY] ?: return null
        val rule = p[RULE]?.takeIf(String::isNotBlank) ?: return null
        val received = p[RECEIVED] ?: return null
        return TriggerSnapshot(AlarmToken(token), source, key, rule, p[TITLE], p[PREVIEW], received)
    }
    private fun putSnapshot(p: MutablePreferences, t: TriggerSnapshot) {
        p[TOKEN] = t.alarmToken.value; p[SOURCE] = t.sourcePackage; p[NOTIFICATION_KEY] = t.notificationKey
        p[RULE] = t.ruleId; p[RECEIVED] = t.receivedAtEpochMs
        t.title?.let { p[TITLE] = it.take(300) }; t.textPreview?.let { p[PREVIEW] = it.take(300) }
    }
    companion object {
        private val BOOT = intPreferencesKey("boot")
        private val OWNER_NONCE = stringPreferencesKey("owner_nonce")
        private val STATE = stringPreferencesKey("state")
        private val TOKEN = stringPreferencesKey("token")
        private val SOURCE = stringPreferencesKey("source")
        private val NOTIFICATION_KEY = stringPreferencesKey("notification_key")
        private val RULE = stringPreferencesKey("rule")
        private val TITLE = stringPreferencesKey("title")
        private val PREVIEW = stringPreferencesKey("preview")
        private val RECEIVED = longPreferencesKey("received")
        private val AT = longPreferencesKey("at")
    }
}
