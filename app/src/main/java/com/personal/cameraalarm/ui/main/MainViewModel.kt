package com.personal.cameraalarm.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import com.personal.cameraalarm.permission.ReadinessState
import com.personal.cameraalarm.schedule.ActiveScheduleGate
import com.personal.cameraalarm.schedule.ScheduleConfiguration
import com.personal.cameraalarm.schedule.ScheduleDecision
import com.personal.cameraalarm.schedule.ScheduleMode
import com.personal.cameraalarm.R
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppStatus { READY, NEEDS_SETUP, ALARMING, STANDBY }

data class MainUiState(
    val readiness: ReadinessState,
    val volumeStatus: AlarmVolumeStatus,
    val monitoringEnabled: Boolean = false,
    val effectiveStatus: AppStatus = AppStatus.NEEDS_SETUP,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val enabledRuleCount: Int = 0,
    val alarmDelayMs: Long = 1000L,
    val cooldownMs: Long = 600000L,
    val vibrationEnabled: Boolean = true,
    val isRinging: Boolean = false,
    val isTestingAlarm: Boolean = false,
    val canStartTestAlarm: Boolean = true,
    val lastDecision: String? = null,
    val lastDecisionTime: Long? = null,
    val userMessage: String? = null,
    val scheduleMode: ScheduleMode = ScheduleMode.ALWAYS_ACTIVE,
    val scheduleActive: Boolean = true,
    val scheduleDescription: String? = null,
    val activeScheduleRangesCount: Int = 0,
    val singleScheduleRangeSummary: String? = null,
    val nextActiveTime: String? = null,
    val alarmSoundName: String = AlarmSoundCatalog.DEFAULT_KEY
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val userMessage = MutableStateFlow<String?>(null)
    private val readinessTick = MutableStateFlow(0)

    val uiState: StateFlow<MainUiState> = combine(
        container.settingsRepository.settings,
        container.ruleRepository.rules,
        container.coordinator.state,
        container.historyRepository.events,
        container.testAlarmToken,
        userMessage,
        readinessTick
    ) { args ->
        val settings = args[0] as com.personal.cameraalarm.data.settings.AppSettings
        @Suppress("UNCHECKED_CAST")
        val rules = args[1] as List<com.personal.cameraalarm.trigger.TriggerRule>
        val alarmState = args[2] as AlarmState
        @Suppress("UNCHECKED_CAST")
        val events = args[3] as List<com.personal.cameraalarm.data.history.AlertEventEntity>
        val testToken = args[4] as? AlarmToken
        val testing = testToken != null
        val message = args[5] as String?

        val readiness = container.readiness.snapshot()
        val volume = container.readiness.volumeStatus()
        val isRinging = alarmState is AlarmState.Ringing || testing
        val enabledRules = rules.filter { it.enabled && it.sourcePackage.isNotBlank() }

        val scheduleConfig = ScheduleConfiguration(settings.scheduleMode, settings.scheduleRanges)
        val now = System.currentTimeMillis()
        val scheduleDecision = ActiveScheduleGate.evaluate(scheduleConfig, now)
        val isScheduleActive = scheduleDecision == ScheduleDecision.ACTIVE
        val nextActive = ActiveScheduleGate.nextActiveTime(scheduleConfig, now)
        val soundName = AlarmSoundCatalog.resolve(settings.alarmSoundKey).key
        val enabledRanges = settings.scheduleRanges.filter { it.enabled }
        val singleRangeText = if (enabledRanges.size == 1) {
            "${enabledRanges[0].formatStart()} - ${enabledRanges[0].formatEnd()}"
        } else null
        val scheduleDesc = if (settings.scheduleMode == ScheduleMode.ALWAYS_ACTIVE) {
            container.getString(R.string.schedule_always_active)
        } else {
            if (enabledRanges.isEmpty()) container.getString(R.string.diag_none)
            else if (enabledRanges.size == 1) singleRangeText
            else container.getString(R.string.schedule_summary_count, enabledRanges.size)
        }

        val effectiveStatus = when {
            isRinging -> AppStatus.ALARMING
            !readiness.blockingReady -> AppStatus.NEEDS_SETUP
            settings.monitoringEnabled && readiness.readyForMonitoring -> {
                if (settings.scheduleMode == ScheduleMode.CUSTOM && !isScheduleActive) AppStatus.STANDBY
                else AppStatus.READY
            }
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
            canStartTestAlarm = AlarmRuntimeOwnership.canStartTest(alarmState, testToken),
            lastDecision = lastEvent?.decision,
            lastDecisionTime = lastEvent?.createdAtEpochMs,
            userMessage = message,
            scheduleMode = settings.scheduleMode,
            scheduleActive = isScheduleActive,
            scheduleDescription = scheduleDesc,
            activeScheduleRangesCount = enabledRanges.size,
            singleScheduleRangeSummary = singleRangeText,
            nextActiveTime = nextActive,
            alarmSoundName = soundName
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
                    userMessage.value = container.getString(R.string.message_monitoring_setup_required)
                    return@launch
                }
            }
            container.settingsRepository.setMonitoringEnabled(enabled)
        }
    }

    fun startTestAlarm(context: Context) {
        container.testAlarmController.start(context).onFailure { error ->
            container.runtimeDiagnostics.record("test alarm: ${error.message ?: error.javaClass.simpleName}")
            userMessage.value = container.getString(R.string.message_test_alarm_failed)
        }
    }

    fun stopAlarm(context: Context) {
        container.testAlarmController.stop(context)
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
