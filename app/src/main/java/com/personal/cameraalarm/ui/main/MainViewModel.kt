package com.personal.cameraalarm.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
    val cooldownMs: Long = 10000L,
    val vibrationEnabled: Boolean = true,
    val isRinging: Boolean = false,
    val isTestingAlarm: Boolean = false,
    val lastDecision: String? = null,
    val lastDecisionTime: Long? = null,
    val userMessage: String? = null,
    val scheduleMode: ScheduleMode = ScheduleMode.ALWAYS_ACTIVE,
    val scheduleActive: Boolean = true,
    val scheduleDescription: String? = null,
    val activeScheduleRangesCount: Int = 0,
    val singleScheduleRangeSummary: String? = null,
    val nextActiveTime: String? = null,
    val alarmSoundName: String = "Default Alarm"
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
        val testing = args[4] != null
        val message = args[5] as String?

        val readiness = container.readiness.snapshot()
        val volume = container.readiness.volumeStatus()
        val isRinging = alarmState is AlarmState.Ringing || testing
        val enabledRules = rules.filter { it.enabled && it.sourcePackage == settings.sourcePackage }

        val scheduleConfig = ScheduleConfiguration(settings.scheduleMode, settings.scheduleRanges)
        val now = System.currentTimeMillis()
        val scheduleDecision = ActiveScheduleGate.evaluate(scheduleConfig, now)
        val isScheduleActive = scheduleDecision == ScheduleDecision.ACTIVE
        val nextActive = ActiveScheduleGate.nextActiveTime(scheduleConfig, now)
        val soundName = AlarmSoundCatalog.resolve(settings.alarmSoundKey).displayName
        val enabledRanges = settings.scheduleRanges.filter { it.enabled }
        val singleRangeText = if (enabledRanges.size == 1) {
            "${enabledRanges[0].formatStart()} - ${enabledRanges[0].formatEnd()}"
        } else null
        val scheduleDesc = if (settings.scheduleMode == ScheduleMode.ALWAYS_ACTIVE) {
            "Always active"
        } else {
            if (enabledRanges.isEmpty()) "None"
            else if (enabledRanges.size == 1) singleRangeText
            else "${enabledRanges.size} ranges"
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
                    userMessage.value = "Cannot enable: Complete required setup items first."
                    return@launch
                }
            }
            container.settingsRepository.setMonitoringEnabled(enabled)
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
            val directIntent = Intent(context, com.personal.cameraalarm.ui.alarm.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = android.net.Uri.parse("cameraalarm://alarm_full/${testToken.value}")
                putExtra(AlarmReceiver.EXTRA_TOKEN, testToken.value)
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TITLE, context.getString(com.personal.cameraalarm.R.string.test_alarm_title))
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_PREVIEW, context.getString(com.personal.cameraalarm.R.string.test_alarm_preview))
                putExtra(com.personal.cameraalarm.ui.alarm.AlarmActivity.EXTRA_TIME, System.currentTimeMillis())
            }
            context.startActivity(directIntent)
        } catch (error: RuntimeException) {
            container.testAlarmToken.compareAndSet(testToken, null)
            userMessage.value = "Unable to start Test Alarm: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun stopAlarm(context: Context) {
        val token = container.testAlarmToken.value
            ?: (container.coordinator.state.value as? AlarmState.Ringing)?.trigger?.alarmToken
            ?: return
        val intent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = StopAlarmReceiver.ACTION_STOP
            putExtra(AlarmReceiver.EXTRA_TOKEN, token.value)
        }
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
