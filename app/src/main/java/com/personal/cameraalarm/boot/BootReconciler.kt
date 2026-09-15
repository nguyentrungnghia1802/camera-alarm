package com.personal.cameraalarm.boot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import com.personal.cameraalarm.alarm.AlarmState
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.notification.CameraNotificationListener
import com.personal.cameraalarm.notification.ListenerStatus

interface BootReconciler {
    suspend fun reconcile()
}

class DefaultBootReconciler(
    private val context: Context,
    private val container: AppContainer
) : BootReconciler {
    override suspend fun reconcile() {
        // 0. Ensure persisted settings and rules are fully loaded into memory before checking readiness
        container.awaitConfigLoaded()

        // 1. Reconcile alarm runtime state to safe Idle (clearing any orphan PENDING or RINGING)
        try {
            val currentState = container.stateStore.read()
            if (currentState !is AlarmState.Idle) {
                container.stateStore.write(AlarmState.Idle)
            }
        } catch (e: Exception) {
            container.runtimeDiagnostics.record("boot stateStore: ${e.message ?: e.javaClass.simpleName}")
        }

        // 2. Clear test alarm token if lingering
        container.testAlarmToken.value = null

        // 3. Check readiness dynamically (now with accurate configuration loaded)
        val readiness = container.readiness.snapshot()

        // 4. Request rebind for NotificationListenerService if access is granted
        if (readiness.notificationAccessGranted) {
            rebindNotificationListener()
        }

        // 5. Record boot diagnostics / history event
        try {
            container.historyRepository.recordEvent(
                createdAtEpochMs = System.currentTimeMillis(),
                sourcePackage = container.triggerConfiguration.sourcePackage,
                notificationKey = null,
                title = "System Boot",
                textPreview = "Boot completed; state reconciled to Idle; readiness checked",
                normalizedHash = null,
                decision = "BOOT_RECONCILED",
                ruleId = null,
                alarmToken = null,
                details = "monitoring=${container.triggerConfiguration.monitoringEnabled}, access=${readiness.notificationAccessGranted}"
            )
        } catch (e: Exception) {
            container.runtimeDiagnostics.record("boot history: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun rebindNotificationListener() {
        val component = ComponentName(context, CameraNotificationListener::class.java)
        try {
            NotificationListenerService.requestRebind(component)
        } catch (e: Exception) {
            container.runtimeDiagnostics.record("boot rebind: ${e.message ?: e.javaClass.simpleName}")
        }

        // On OEM devices (Samsung, Xiaomi, Oppo, etc.) after hard reboot,
        // requestRebind alone may not nudge NotificationManagerService if the service was not yet initialized.
        // Nudging component enabled state triggers immediate rebinding in Android OS.
        if (container.listenerConnection.status.value != ListenerStatus.CONNECTED) {
            try {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                NotificationListenerService.requestRebind(component)
            } catch (e: Exception) {
                container.runtimeDiagnostics.record("boot nudge listener: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
