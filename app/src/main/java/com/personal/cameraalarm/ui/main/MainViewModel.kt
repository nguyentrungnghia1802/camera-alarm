package com.personal.cameraalarm.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import com.personal.cameraalarm.permission.ReadinessState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppStatus { READY, NEEDS_SETUP, ALARMING }

data class MainUiState(
    val readiness: ReadinessState,
    val volumeStatus: AlarmVolumeStatus,
    val monitoringEnabled: Boolean = false,
    val effectiveStatus: AppStatus = AppStatus.NEEDS_SETUP,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val enabledRuleCount: Int = 0,
    val alarmDelayMs: Long = 1000L,
    val cooldownMs: Long = 10000L,
    val vibrationEnabled: Boolean = true,
    val isRinging: Boolean = false,
    val isTestingAlarm: Boolean = false,
    val lastDecision: String? = null,
    val lastDecisionTime: Long? = null,
    val userMessage: String? = null
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val testAlarmActive = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<String?>(null)
    private val readinessTick = MutableStateFlow(0)

    val uiState: StateFlow<MainUiState> = combine(
        container.settingsRepository.settings,
        container.ruleRepository.rules,
        container.coordinator.state,
        container.historyRepository.events,
        testAlarmActive,
        userMessage,
        readinessTick
    ) { args ->
        val settings = args[0] as com.personal.cameraalarm.data.settings.AppSettings
        val rules = args[1] as List<com.personal.cameraalarm.trigger.TriggerRule>
        val alarmState = args[2] as AlarmState
        val events = args[3] as List<com.personal.cameraalarm.data.history.AlertEventEntity>
        val testing = args[4] as Boolean
        val message = args[5] as String?

        val readiness = container.readiness.snapshot()
        val volume = container.readiness.volumeStatus()
        val isRinging = alarmState is AlarmState.Ringing || testing
        val enabledRules = rules.filter { it.enabled && it.sourcePackage == settings.sourcePackage }

        val effectiveStatus = when {
            isRinging -> AppStatus.ALARMING
            !readiness.blockingReady -> AppStatus.NEEDS_SETUP
            settings.monitoringEnabled && readiness.readyForMonitoring -> AppStatus.READY
            settings.monitoringEnabled && !readiness.readyForMonitoring -> AppStatus.NEEDS_SETUP
            else -> AppStatus.NEEDS_SETUP
        }

        val lastEvent = events.firstOrNull()

        MainUiState(
            readiness = readiness,
            volumeStatus = volume,
            monitoringEnabled = settings.monitoringEnabled,
            effectiveStatus = effectiveStatus,
            sourcePackage = settings.sourcePackage,
            sourceLabel = settings.sourceLabel,
            enabledRuleCount = enabledRules.size,
            alarmDelayMs = settings.alarmDelayMs,
            cooldownMs = settings.cooldownMs,
            vibrationEnabled = settings.vibrationEnabled,
            isRinging = isRinging,
            isTestingAlarm = testing,
            lastDecision = lastEvent?.decision,
            lastDecisionTime = lastEvent?.createdAtEpochMs,
            userMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState(
            readiness = container.readiness.snapshot(),
            volumeStatus = container.readiness.volumeStatus()
        )
    )

    fun refreshReadiness() {
        readinessTick.value = readinessTick.value + 1
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val currentReadiness = container.readiness.snapshot()
                if (!currentReadiness.blockingReady) {
                    userMessage.value = "Cannot enable: Complete required setup items first."
                    return@launch
                }
            }
            container.settingsRepository.setMonitoringEnabled(enabled)
        }
    }

    fun startTestAlarm(context: Context) {
        val testToken = AlarmToken("test-${System.currentTimeMillis()}")
        val intent = Intent(context, CameraAlarmService::class.java).apply {
            action = CameraAlarmService.ACTION_START
            putExtra(AlarmReceiver.EXTRA_TOKEN, testToken.value)
            putExtra(CameraAlarmService.EXTRA_IS_TEST, true)
        }
        testAlarmActive.value = true
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopAlarm(context: Context) {
        val intent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = StopAlarmReceiver.ACTION_STOP
        }
        testAlarmActive.value = false
        context.sendBroadcast(intent)
    }

    fun clearUserMessage() {
        userMessage.value = null
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container) as T
            }
    }
}
