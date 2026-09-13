package com.personal.cameraalarm.boot

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import com.personal.cameraalarm.alarm.AlarmState
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.notification.CameraNotificationListener

interface BootReconciler {
    suspend fun reconcile()
}

class DefaultBootReconciler(
    private val context: Context,
    private val container: AppContainer
) : BootReconciler {
    override suspend fun reconcile() {
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

        // 3. Check readiness dynamically
        val readiness = container.readiness.snapshot()

        // 4. Request rebind for NotificationListenerService if access is granted
        if (readiness.notificationAccessGranted) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, CameraNotificationListener::class.java)
                )
            } catch (e: Exception) {
                container.runtimeDiagnostics.record("boot rebind: ${e.message ?: e.javaClass.simpleName}")
            }
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
}
