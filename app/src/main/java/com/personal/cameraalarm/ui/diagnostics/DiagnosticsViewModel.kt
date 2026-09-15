package com.personal.cameraalarm.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.alarm.AlarmReceiver
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.CameraAlarmService
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.permission.AlarmVolumeStatus
import com.personal.cameraalarm.permission.ReadinessState
import com.personal.cameraalarm.reliability.DeviceReliabilityAdvisor
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
    val fullScreenIntentAllowed: Boolean = true
)

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {
    private val copyMessage = MutableStateFlow<String?>(null)
    val advisorRegistry = container.advisorRegistry
    private val selectedOemKey = MutableStateFlow<String>(advisorRegistry.activeAdvisor.oemKey)

    fun selectOem(key: String) {
        selectedOemKey.value = key
    }

    fun currentAdvisor(): DeviceReliabilityAdvisor = advisorRegistry.getAdvisorByKey(selectedOemKey.value)

    private val baseDiagnostics = combine(
        container.settingsRepository.settings,
        container.ruleRepository.rules,
        container.coordinator.state,
        container.runtimeDiagnostics.lastError,
        copyMessage
    ) { settings, rules, alarmState, lastError, msg ->
        val readiness = container.readiness.snapshot()
        val volume = container.readiness.volumeStatus()
        val enabledRules = rules.filter { it.enabled && it.sourcePackage == settings.sourcePackage }
        val canFullScreen = container.readiness.canUseFullScreenIntent()

        Tuple5(settings, enabledRules.size, alarmState, lastError, msg to Pair(readiness, Pair(volume, canFullScreen)))
    }

    val uiState: StateFlow<Pair<DiagnosticsInfo, String?>> = combine(
        baseDiagnostics,
        selectedOemKey
    ) { base, oemKey ->
        val (settings, ruleCount, alarmState, lastError, rest) = base
        val (msg, readPair) = rest
        val (readiness, volPair) = readPair
        val (volume, canFullScreen) = volPair

        val activeAdvisor = advisorRegistry.activeAdvisor
        val currentAdvisor = advisorRegistry.getAdvisorByKey(oemKey)

        val info = DiagnosticsInfo(
            appVersion = "1.0 (1)",
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
            fullScreenIntentAllowed = canFullScreen
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
            appendLine("=== CAMERA ALARM DIAGNOSTICS & RELIABILITY ===")
            appendLine("App Version: ${info.appVersion}")
            appendLine("Android SDK: API ${info.sdkInt} (${Build.VERSION.RELEASE})")
            appendLine("Device: ${info.deviceModel}")
            appendLine("Manufacturer: ${info.manufacturer}")
            appendLine("Brand: ${info.brand}")
            appendLine("Detected OEM Family: ${info.activeOemName}")
            appendLine("Selected Guide: ${info.selectedOemKey}")
            appendLine("Battery Optimizations Ignored: ${batteryIgnored ?: "UNKNOWN"}")
            appendLine("Notification Access: ${if (info.readiness.notificationAccessGranted) "GRANTED" else "REQUIRED"}")
            appendLine("Listener Connection: ${if (info.readiness.listenerConnected) "CONNECTED" else "DISCONNECTED"}")
            appendLine("Exact Alarm: ${if (info.readiness.exactAlarmGranted) "GRANTED" else "REQUIRED"}")
            appendLine("Post Notifications: ${if (info.readiness.postNotificationsGranted) "GRANTED" else "DENIED"}")
            appendLine("Full Screen Intent: ${if (info.fullScreenIntentAllowed) "ALLOWED" else "DISALLOWED"}")
            appendLine("Alarm Volume: current=${info.volumeStatus.current}, min=${info.volumeStatus.minimum}, max=${info.volumeStatus.maximum}")
            appendLine("Monitoring Enabled: ${info.monitoringEnabled}")
            appendLine("Source Package: ${info.sourcePackage ?: "None"}")
            appendLine("Enabled Rules: ${info.enabledRuleCount}")
            appendLine("Current State: ${info.alarmState}")
            appendLine("Last Runtime Error: ${info.lastRuntimeError ?: "None"}")
            appendLine("==============================================")
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Camera Alarm Diagnostics", report))
        copyMessage.value = "Diagnostics copied to clipboard."
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
            copyMessage.value = "Unable to start Test Alarm: ${error.message ?: error.javaClass.simpleName}"
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
