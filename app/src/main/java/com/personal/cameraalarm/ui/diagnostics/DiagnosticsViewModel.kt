package com.personal.cameraalarm.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.alarm.AlarmRuntimeOwnership
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import com.personal.cameraalarm.permission.ReadinessState
import com.personal.cameraalarm.reliability.DeviceReliabilityAdvisor
import com.personal.cameraalarm.BuildConfig
import com.personal.cameraalarm.R
import kotlinx.coroutines.flow.*

data class DiagnosticsInfo(
    val appVersion: String = "1.0",
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val manufacturer: String = Build.MANUFACTURER,
    val brand: String = Build.BRAND,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val activeOemName: String = "Generic Android",
    val selectedOemKey: String = "generic",
    val isOemMatch: Boolean = true,
    val batteryOptimizationsIgnored: Boolean = false,
    val readiness: ReadinessState = ReadinessState(
        notificationAccessGranted = false,
        listenerConnected = false,
        exactAlarmGranted = false,
        postNotificationsGranted = false,
        sourceConfigured = false,
        ruleConfigured = false,
        alarmVolumeNonZero = false
    ),
    val volumeStatus: AlarmVolumeStatus = AlarmVolumeStatus(0, 0, 0),
    val monitoringEnabled: Boolean = false,
    val sourcePackage: String? = null,
    val enabledRuleCount: Int = 0,
    val alarmState: String = "Idle",
    val lastRuntimeError: String? = null,
    val fullScreenIntentAllowed: Boolean = true,
    val canStartTestAlarm: Boolean = true
)

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {
    private val copyMessage = MutableStateFlow<String?>(null)
    val advisorRegistry = container.advisorRegistry
    private val selectedOemKey = MutableStateFlow<String>(advisorRegistry.activeAdvisor.oemKey)

    fun selectOem(key: String) {
        selectedOemKey.value = key
    }

    fun currentAdvisor(): DeviceReliabilityAdvisor = advisorRegistry.getAdvisorByKey(selectedOemKey.value)

    private val alarmOwnership = combine(
        container.coordinator.state,
        container.testAlarmToken
    ) { state, testToken -> state to testToken }

    private val baseDiagnostics = combine(
        container.settingsRepository.settings,
        container.ruleRepository.rules,
        alarmOwnership,
        container.runtimeDiagnostics.lastError,
        copyMessage
    ) { settings, rules, ownership, lastError, msg ->
        val (alarmState, testToken) = ownership
        val readiness = container.readiness.snapshot()
        val volume = container.readiness.volumeStatus()
        val enabledRules = rules.filter { it.enabled && it.sourcePackage == settings.sourcePackage }
        val canFullScreen = container.readiness.canUseFullScreenIntent()

        Tuple5(settings, enabledRules.size, alarmState to testToken, lastError, msg to Pair(readiness, Pair(volume, canFullScreen)))
    }

    val uiState: StateFlow<Pair<DiagnosticsInfo, String?>> = combine(
        baseDiagnostics,
        selectedOemKey
    ) { base, oemKey ->
        val (settings, ruleCount, ownership, lastError, rest) = base
        val (alarmState, testToken) = ownership
        val (msg, readPair) = rest
        val (readiness, volPair) = readPair
        val (volume, canFullScreen) = volPair

        val activeAdvisor = advisorRegistry.activeAdvisor
        val currentAdvisor = advisorRegistry.getAdvisorByKey(oemKey)

        val info = DiagnosticsInfo(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            activeOemName = activeAdvisor.deviceFamilyName,
            selectedOemKey = oemKey,
            isOemMatch = currentAdvisor.isApplicable,
            batteryOptimizationsIgnored = false,
            readiness = readiness,
            volumeStatus = volume,
            monitoringEnabled = settings.monitoringEnabled,
            sourcePackage = settings.sourcePackage,
            enabledRuleCount = ruleCount,
            alarmState = alarmState::class.simpleName ?: "Unknown",
            lastRuntimeError = lastError,
            fullScreenIntentAllowed = canFullScreen,
            canStartTestAlarm = AlarmRuntimeOwnership.canStartTest(alarmState, testToken)
        )
        info to msg
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DiagnosticsInfo(
            readiness = container.readiness.snapshot(),
            volumeStatus = container.readiness.volumeStatus()
        ) to null
    )

    fun copyDiagnostics(context: Context, info: DiagnosticsInfo) {
        val pm = context.getSystemService(PowerManager::class.java)
        val batteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName)

        val report = buildString {
            appendLine("=== ${context.getString(R.string.title_diagnostics).uppercase()} ===")
            appendLine("${context.getString(R.string.diag_app_version)}: ${info.appVersion}")
            appendLine("${context.getString(R.string.diag_android_version)}: API ${info.sdkInt} (${Build.VERSION.RELEASE})")
            appendLine("${context.getString(R.string.diag_device)}: ${info.deviceModel}")
            appendLine("${context.getString(R.string.diag_manufacturer)}: ${info.manufacturer} / ${info.brand}")
            appendLine("${context.getString(R.string.diag_active_oem)}: ${info.activeOemName}")
            appendLine("${context.getString(R.string.diag_battery_unrestricted)}: ${batteryIgnored ?: context.getString(R.string.status_unknown)}")
            appendLine("${context.getString(R.string.diag_notif_access)}: ${if (info.readiness.notificationAccessGranted) context.getString(R.string.status_granted) else context.getString(R.string.status_required)}")
            appendLine("${context.getString(R.string.diag_listener_conn)}: ${info.readiness.listenerStatus.name}")
            appendLine("${context.getString(R.string.diag_exact_alarm)}: ${if (info.readiness.exactAlarmGranted) context.getString(R.string.status_granted) else context.getString(R.string.status_required)}")
            appendLine("${context.getString(R.string.diag_app_notif)}: ${if (info.readiness.postNotificationsGranted) context.getString(R.string.status_granted) else context.getString(R.string.status_denied)}")
            appendLine("${context.getString(R.string.diag_fullscreen)}: ${if (info.fullScreenIntentAllowed) context.getString(R.string.diag_allowed) else context.getString(R.string.diag_disallowed)}")
            appendLine(context.getString(R.string.diag_volume_detail, info.volumeStatus.current, info.volumeStatus.minimum, info.volumeStatus.maximum))
            appendLine("${context.getString(R.string.diag_monitoring_enabled)}: ${info.monitoringEnabled}")
            appendLine("${context.getString(R.string.diag_active_source)}: ${info.sourcePackage ?: context.getString(R.string.diag_none)}")
            appendLine(context.getString(R.string.diag_rules_count_format, info.enabledRuleCount))
            appendLine("${context.getString(R.string.diag_alarm_state)}: ${info.alarmState}")
            appendLine("${context.getString(R.string.diag_last_error)}: ${info.lastRuntimeError ?: context.getString(R.string.diag_none)}")
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Camera Alarm Diagnostics", report))
        copyMessage.value = context.getString(R.string.message_diagnostics_copied)
    }

    fun startTestAlarm(context: Context) {
        container.testAlarmController.start(context).onFailure { error ->
            container.runtimeDiagnostics.record("test alarm: ${error.message ?: error.javaClass.simpleName}")
            copyMessage.value = context.getString(R.string.message_test_alarm_failed)
        }
    }

    fun clearCopyMessage() {
        copyMessage.value = null
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiagnosticsViewModel(container) as T
            }
    }
}
