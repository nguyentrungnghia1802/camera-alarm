package com.personal.cameraalarm.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.alarm.sound.AlarmSound
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.data.settings.AppSettings
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val draftSettings: AppSettings = AppSettings(),
    val isModified: Boolean = false,
    val saveSuccess: Boolean = false,
    val volumeStatus: AlarmVolumeStatus = AlarmVolumeStatus(7, 0, 7),
    val isTestingAlarm: Boolean = false,
    val previewPlayingKey: String? = null,
    val historyCount: Int = 0,
    val message: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val userMessage = MutableStateFlow<String?>(null)
    private val saveSuccess = MutableStateFlow(false)
    private val draftState = MutableStateFlow<AppSettings?>(null)
    val availableSounds: List<AlarmSound> = AlarmSoundCatalog.allSounds

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settings,
        draftState,
        saveSuccess,
        container.historyRepository.events,
        container.testAlarmToken,
        container.soundPreviewController.playingSoundKey,
        userMessage
    ) { args ->
        val persisted = args[0] as AppSettings
        val draft = (args[1] as? AppSettings) ?: persisted
        val saved = args[2] as Boolean
        val events = args[3] as List<*>
        val testToken = args[4]
        val previewKey = args[5] as? String
        val message = args[6] as? String
        val volume = container.readiness.volumeStatus()
        val isModified = (draft != persisted)

        SettingsUiState(
            settings = persisted,
            draftSettings = draft,
            isModified = isModified,
            saveSuccess = saved,
            volumeStatus = volume,
            isTestingAlarm = testToken != null,
            previewPlayingKey = previewKey,
            historyCount = events.size,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { persisted ->
                if (draftState.value == null) {
                    draftState.value = persisted
                }
            }
        }
    }

    private fun updateDraft(transform: (AppSettings) -> AppSettings) {
        val current = draftState.value ?: AppSettings()
        draftState.value = transform(current)
        saveSuccess.value = false
    }

    fun setDelay(delayMs: Long) {
        updateDraft { it.copy(alarmDelayMs = delayMs) }
    }

    fun setCooldown(cooldownMs: Long) {
        updateDraft { it.copy(cooldownMs = cooldownMs) }
    }

    fun saveCooldown(cooldownMs: Long, onSuccess: (() -> Unit)? = null) {
        setCooldown(cooldownMs)
        val draft = draftState.value ?: return
        viewModelScope.launch {
            container.settingsRepository.updateAll(draft)
            container.coordinator.resetCooldown()
            saveSuccess.value = true
            onSuccess?.invoke()
        }
    }

    fun setVibration(enabled: Boolean) {
        updateDraft { it.copy(vibrationEnabled = enabled) }
    }

    fun setFullScreen(enabled: Boolean) {
        updateDraft { it.copy(fullScreenEnabled = enabled) }
    }

    fun selectAlarmSound(soundKey: String) {
        updateDraft { it.copy(alarmSoundKey = soundKey) }
    }

    fun setScheduleMode(mode: com.personal.cameraalarm.schedule.ScheduleMode) {
        updateDraft { it.copy(scheduleMode = mode) }
    }

    fun setLanguage(language: String) {
        updateDraft { it.copy(language = language) }
    }

    fun addScheduleRange(startMinutes: Int, endMinutes: Int) {
        updateDraft { current ->
            val newRange = com.personal.cameraalarm.schedule.ActiveTimeRange(
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                enabled = true
            )
            current.copy(scheduleRanges = current.scheduleRanges + newRange)
        }
    }

    fun updateScheduleRange(id: String, startMinutes: Int, endMinutes: Int, enabled: Boolean) {
        updateDraft { current ->
            val updated = current.scheduleRanges.map {
                if (it.id == id) it.copy(startMinutes = startMinutes, endMinutes = endMinutes, enabled = enabled)
                else it
            }
            current.copy(scheduleRanges = updated)
        }
    }

    fun toggleScheduleRange(id: String, enabled: Boolean) {
        updateDraft { current ->
            val updated = current.scheduleRanges.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            current.copy(scheduleRanges = updated)
        }
    }

    fun deleteScheduleRange(id: String) {
        updateDraft { current ->
            val updated = current.scheduleRanges.filterNot { it.id == id }
            current.copy(scheduleRanges = updated)
        }
    }

    fun saveSettings(onSuccess: (() -> Unit)? = null) {
        val draft = draftState.value ?: return
        viewModelScope.launch {
            val oldCooldown = try { container.settingsRepository.current().cooldownMs } catch (_: Exception) { null }
            container.settingsRepository.updateAll(draft)
            if (oldCooldown != null && oldCooldown != draft.cooldownMs) {
                container.coordinator.resetCooldown()
            }
            saveSuccess.value = true
            onSuccess?.invoke()
        }
    }

    fun saveAlarmSound(soundKey: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            container.settingsRepository.setAlarmSound(soundKey)
            updateDraft { it.copy(alarmSoundKey = soundKey) }
            saveSuccess.value = true
            onSuccess?.invoke()
        }
    }

    fun discardChanges() {
        viewModelScope.launch {
            val persisted = container.settingsRepository.current()
            draftState.value = persisted
            saveSuccess.value = false
        }
    }

    fun playPreview(sound: AlarmSound) {
        val result = container.soundPreviewController.play(sound)
        if (result.isFailure) {
            userMessage.value = "Failed to preview sound: ${result.exceptionOrNull()?.message}"
        }
    }

    fun stopPreview() {
        container.soundPreviewController.stop()
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.historyRepository.clearHistory()
            userMessage.value = "History cleared."
        }
    }

    fun startTestAlarm(context: Context) {
        if (container.testAlarmToken.value != null) return
        val testToken = AlarmToken("test-${System.currentTimeMillis()}")
        val intent = Intent(context, CameraAlarmService::class.java).apply {
            action = CameraAlarmService.ACTION_START
            putExtra(AlarmReceiver.EXTRA_TOKEN, testToken.value)
            putExtra(CameraAlarmService.EXTRA_IS_TEST, true)
        }
        container.testAlarmToken.value = testToken
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (error: RuntimeException) {
            container.testAlarmToken.compareAndSet(testToken, null)
            userMessage.value = "Unable to start Test Alarm: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun stopAlarm(context: Context) {
        val token = container.testAlarmToken.value ?: return
        val intent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = StopAlarmReceiver.ACTION_STOP
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
        }
        context.sendBroadcast(intent)
    }

    fun clearMessage() {
        userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        container.soundPreviewController.stop()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(container) as T
            }
    }
}
