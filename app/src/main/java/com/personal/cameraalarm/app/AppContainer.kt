package com.personal.cameraalarm.app

import android.content.Context
import androidx.annotation.StringRes
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.data.AppDatabase
import com.personal.cameraalarm.data.history.HistoryRepository
import com.personal.cameraalarm.data.history.AlarmHistoryEventFactory
import com.personal.cameraalarm.data.rule.TriggerRuleRepository
import com.personal.cameraalarm.data.settings.SettingsRepository
import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.permission.ExactAlarmAccess
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.trigger.*
import com.personal.cameraalarm.util.AndroidClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    fun getString(@StringRes resourceId: Int, vararg formatArgs: Any): String =
        appContext.getString(resourceId, *formatArgs)

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

    val alarmRuntimeConfig = AlarmRuntimeConfigCache()

    val initialConfigLoaded = CompletableDeferred<Unit>()

    suspend fun awaitConfigLoaded(timeoutMs: Long = 3000L): Boolean =
        withTimeoutOrNull(timeoutMs) { initialConfigLoaded.await(); true } ?: false

    val scheduler = AndroidAlarmScheduler(context)
    val recoveryJobs = com.personal.cameraalarm.boot.RecoveryJobs(appContext)
    val readiness = ReadinessRepository(context, exactAlarmAccess, listenerConnection) { triggerConfiguration }
    val advisorRegistry = com.personal.cameraalarm.reliability.DeviceReliabilityAdvisorRegistry(readiness)
    val deviceAdvisor: com.personal.cameraalarm.reliability.DeviceReliabilityAdvisor =
        advisorRegistry.activeAdvisor

    private val appScope = (context.applicationContext as? CameraAlarmApp)?.scope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        appScope.launch {
            historyRepository.deleteLegacyUnrelatedNotificationRows()
        }
        appScope.launch {
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
                alarmRuntimeConfig.update(
                    AlarmRuntimeConfig(
                        vibrationEnabled = settings.vibrationEnabled,
                        fullScreenEnabled = settings.fullScreenEnabled,
                        soundKey = settings.alarmSoundKey
                    )
                )
                if (!initialConfigLoaded.isCompleted) {
                    initialConfigLoaded.complete(Unit)
                }
            }.retryWhen { error, attempt ->
                if (error is CancellationException) return@retryWhen false
                runtimeDiagnostics.record("config retry: ${error.message}")
                delay((1000L * (attempt + 1)).coerceAtMost(30_000))
                true
            }.collect()
        }
    }

    val coordinator = AlarmCoordinator(
        AndroidClock,
        scheduler,
        stateStore,
        { alarmPolicy },
        AlarmEffectObserver { effect ->
            if (effect is AlarmEffect.RecordFailure) {
                runtimeDiagnostics.record(effect.reason)
            }
            val event = AlarmHistoryEventFactory.fromEffect(
                effect = effect,
                createdAtEpochMs = System.currentTimeMillis(),
                fallbackSourcePackage = triggerConfiguration.sourcePackage
            )
            if (event != null) {
                appScope.launch {
                    try {
                        historyRepository.recordEvent(
                            createdAtEpochMs = event.createdAtEpochMs,
                            sourcePackage = event.sourcePackage,
                            notificationKey = event.notificationKey,
                            title = event.title,
                            textPreview = event.textPreview,
                            normalizedHash = event.normalizedHash,
                            decision = event.decision,
                            ruleId = event.ruleId,
                            alarmToken = event.alarmToken,
                            details = event.details
                        )
                    } catch (error: Exception) {
                        runtimeDiagnostics.record(
                            "history lifecycle: ${error.message ?: error.javaClass.simpleName}"
                        )
                    }
                }
            }
        },
        recoveryScheduler = recoveryJobs,
        elapsedMs = { android.os.SystemClock.elapsedRealtime() },
        trace = { stage, trigger, details -> AlarmTrace.record(stage, trigger = trigger, details = details) }
    )

    val testAlarmController = TestAlarmController(this)

    init {
        appScope.launch {
            var lastCooldown: Long? = null
            settingsRepository.settings
                .map { it.cooldownMs }
                .distinctUntilChanged()
                .collect { newCooldown ->
                    if (lastCooldown != null && lastCooldown != newCooldown) {
                        coordinator.resetCooldown()
                    }
                    lastCooldown = newCooldown
                }
        }
    }

    val pipeline = TriggerPipeline(
        AndroidClock,
        TriggerConfigurationSource {
            // Do not interpret startup defaults as the user's monitoring OFF setting.
            initialConfigLoaded.await()
            coordinator.reconcile()
            triggerConfiguration
        },
        TtlDuplicateGuard(),
        coordinator,
        TriggerHistory { notification, decision, token, ruleId ->
            if (com.personal.cameraalarm.BuildConfig.DEBUG) {
                android.util.Log.d("CameraAlarm", "decision=$decision source=${notification.packageName}")
            }
            val details = if (decision == TriggerDecision.SUPPRESSED_COOLDOWN) {
                val state = try { coordinator.snapshot() } catch (_: Exception) { null }
                if (state is AlarmState.Cooldown) {
                    val remainingMs = (state.untilEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
                    val min = (remainingMs / 1000) / 60
                    val sec = (remainingMs / 1000) % 60
                    String.format(java.util.Locale.getDefault(), "%02d:%02d", min, sec)
                } else null
            } else null

            historyRepository.recordEvent(
                createdAtEpochMs = notification.postTimeEpochMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                sourcePackage = notification.packageName,
                notificationKey = notification.key,
                title = notification.title,
                textPreview = notification.text ?: notification.bigText ?: notification.subText,
                normalizedHash = null,
                decision = decision.name,
                ruleId = ruleId,
                alarmToken = token?.value,
                details = details
            )
        },
        historyFailure = { error ->
            val reason = "history: ${error.message ?: error.javaClass.simpleName}"
            runtimeDiagnostics.record(reason)
            android.util.Log.e("CameraAlarm", reason, error)
        }
    )

    fun startRecovery() {
        coordinator.watchPending(appScope)
        appScope.launch {
            try { coordinator.reconcile() }
            catch (error: Exception) {
                runtimeDiagnostics.record("startup hydration: ${error.message}")
                recoveryJobs.boot("PROCESS_START")
            }
        }
    }
}
