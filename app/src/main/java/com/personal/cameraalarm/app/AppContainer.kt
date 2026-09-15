package com.personal.cameraalarm.app

import android.content.Context
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.data.AppDatabase
import com.personal.cameraalarm.data.history.HistoryRepository
import com.personal.cameraalarm.data.rule.TriggerRuleRepository
import com.personal.cameraalarm.data.settings.SettingsRepository
import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.permission.ExactAlarmAccess
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.util.AndroidClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val listenerConnection = ListenerConnectionState()
    val runtimeDiagnostics = RuntimeDiagnostics()
    val exactAlarmAccess = ExactAlarmAccess(context)
    val stateStore = DataStoreAlarmStateStore(context)
    val database = AppDatabase.getInstance(context)
    val settingsRepository = SettingsRepository(context)
    val ruleRepository = TriggerRuleRepository(database.triggerRuleDao())
    val historyRepository = HistoryRepository(database.alertEventDao())
    val soundPreviewController = com.personal.cameraalarm.alarm.sound.AlarmSoundPreviewController(context)
    val testAlarmToken = MutableStateFlow<AlarmToken?>(null)

    @Volatile
    var triggerConfiguration = TriggerConfiguration(false, null, emptyList())

    @Volatile
    var alarmPolicy = AlarmPolicy()

    val scheduler = AndroidAlarmScheduler(context)
    val readiness = ReadinessRepository(context, exactAlarmAccess, listenerConnection) { triggerConfiguration }
    val advisorRegistry = com.personal.cameraalarm.reliability.DeviceReliabilityAdvisorRegistry(readiness)
    val deviceAdvisor: com.personal.cameraalarm.reliability.DeviceReliabilityAdvisor =
        advisorRegistry.activeAdvisor

    private val appScope = (context.applicationContext as? CameraAlarmApp)?.scope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        combine(settingsRepository.settings, ruleRepository.rules) { settings, rules ->
            triggerConfiguration = TriggerConfiguration(
                monitoringEnabled = settings.monitoringEnabled,
                sourcePackage = settings.sourcePackage,
                rules = rules,
                scheduleConfiguration = com.personal.cameraalarm.schedule.ScheduleConfiguration(
                    mode = settings.scheduleMode,
                    ranges = settings.scheduleRanges
                )
            )
            alarmPolicy = AlarmPolicy(
                delayMs = settings.alarmDelayMs,
                cooldownMs = settings.cooldownMs,
                vibrationEnabled = settings.vibrationEnabled
            )
        }.launchIn(appScope)
    }

    val coordinator = AlarmCoordinator(
        AndroidClock,
        scheduler,
        stateStore,
        { alarmPolicy },
        AlarmEffectObserver { effect ->
            when (effect) {
                is AlarmEffect.RecordFailure -> {
                    runtimeDiagnostics.record(effect.reason)
                    appScope.launch {
                        historyRepository.recordEvent(
                            createdAtEpochMs = System.currentTimeMillis(),
                            sourcePackage = triggerConfiguration.sourcePackage,
                            notificationKey = null,
                            title = "Alarm Failure",
                            textPreview = effect.reason,
                            normalizedHash = null,
                            decision = "SCHEDULE_FAILED",
                            ruleId = null,
                            alarmToken = null,
                            details = effect.reason
                        )
                    }
                }
                is AlarmEffect.StopRuntime -> {
                    appScope.launch {
                        historyRepository.recordEvent(
                            createdAtEpochMs = System.currentTimeMillis(),
                            sourcePackage = triggerConfiguration.sourcePackage,
                            notificationKey = null,
                            title = "Alarm Stopped",
                            textPreview = "Alarm runtime stopped",
                            normalizedHash = null,
                            decision = "ALARM_STOPPED",
                            ruleId = null,
                            alarmToken = effect.alarmToken?.value,
                            details = null
                        )
                    }
                }
                else -> {}
            }
        }
    )

    val pipeline = TriggerPipeline(
        AndroidClock,
        { triggerConfiguration },
        TtlDuplicateGuard(),
        coordinator,
        TriggerHistory { notification, decision, token ->
            if (com.personal.cameraalarm.BuildConfig.DEBUG) {
                android.util.Log.d("CameraAlarm", "decision=$decision source=${notification.packageName}")
            }
            historyRepository.recordEvent(
                createdAtEpochMs = notification.postTimeEpochMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                sourcePackage = notification.packageName,
                notificationKey = notification.key,
                title = notification.title,
                textPreview = notification.text ?: notification.bigText ?: notification.subText,
                normalizedHash = null,
                decision = decision.name,
                ruleId = null,
                alarmToken = token?.value,
                details = null
            )
        },
        historyFailure = { error ->
            val reason = "history: ${error.message ?: error.javaClass.simpleName}"
            runtimeDiagnostics.record(reason)
            android.util.Log.e("CameraAlarm", reason, error)
        }
    )
}
