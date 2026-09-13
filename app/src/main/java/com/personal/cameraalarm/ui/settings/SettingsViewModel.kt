package com.personal.cameraalarm.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.data.settings.AppSettings
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val volumeStatus: AlarmVolumeStatus = AlarmVolumeStatus(7, 0, 7),
    val isTestingAlarm: Boolean = false,
    val historyCount: Int = 0,
    val message: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settings,
        container.historyRepository.events,
        container.testAlarmToken,
        userMessage
    ) { settings, events, testToken, message ->
        val volume = container.readiness.volumeStatus()
        SettingsUiState(
            settings = settings,
            volumeStatus = volume,
            isTestingAlarm = testToken != null,
            historyCount = events.size,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setDelay(delayMs: Long) {
        viewModelScope.launch { container.settingsRepository.setAlarmDelayMs(delayMs) }
    }

    fun setCooldown(cooldownMs: Long) {
        viewModelScope.launch { container.settingsRepository.setCooldownMs(cooldownMs) }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setVibrationEnabled(enabled) }
    }

    fun setFullScreen(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFullScreenEnabled(enabled) }
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

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(container) as T
            }
    }
}
