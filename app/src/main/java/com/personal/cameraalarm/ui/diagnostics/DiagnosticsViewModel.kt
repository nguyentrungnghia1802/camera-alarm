package com.personal.cameraalarm.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
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

data class DiagnosticsInfo(
    val appVersion: String = "1.0 (1)",
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val readiness: ReadinessState,
    val volumeStatus: AlarmVolumeStatus,
    val monitoringEnabled: Boolean = false,
    val sourcePackage: String? = null,
    val enabledRuleCount: Int = 0,
    val alarmState: String = "Idle",
    val lastRuntimeError: String? = null,
    val fullScreenIntentAllowed: Boolean = true
)

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {
    private val copyMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<Pair<DiagnosticsInfo, String?>> = combine(
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

        val info = DiagnosticsInfo(
            appVersion = "1.0 (1)",
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            readiness = readiness,
            volumeStatus = volume,
            monitoringEnabled = settings.monitoringEnabled,
            sourcePackage = settings.sourcePackage,
            enabledRuleCount = enabledRules.size,
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
        val report = buildString {
            appendLine("=== CAMERA ALARM DIAGNOSTICS ===")
            appendLine("App Version: ${info.appVersion}")
            appendLine("Android SDK: API ${info.sdkInt} (${Build.VERSION.RELEASE})")
            appendLine("Device: ${info.deviceModel}")
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
            appendLine("================================")
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
        } catch (error: RuntimeException) {
            container.testAlarmToken.compareAndSet(testToken, null)
            copyMessage.value = "Unable to start Test Alarm: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun clearCopyMessage() {
        copyMessage.value = null
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiagnosticsViewModel(container) as T
            }
    }
}
