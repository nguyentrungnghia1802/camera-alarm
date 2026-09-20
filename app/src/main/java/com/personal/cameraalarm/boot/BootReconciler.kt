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

internal interface BootReconcilerDependencies {
    suspend fun awaitConfigLoaded()
    suspend fun readAlarmState(): AlarmState
    suspend fun writeAlarmState(state: AlarmState)
    fun clearTestAlarmToken()
    suspend fun recoverNotificationListener(): ListenerRecoveryResult
    suspend fun recordBootEvent()
    fun recordDiagnostic(message: String)
}

class DefaultBootReconciler internal constructor(
    private val dependencies: BootReconcilerDependencies
) : BootReconciler {
    constructor(context: Context, container: AppContainer) : this(
        AndroidBootReconcilerDependencies(context, container)
    )

    override suspend fun reconcile() {
        dependencies.awaitConfigLoaded()

        try {
            if (dependencies.readAlarmState() !is AlarmState.Idle) {
                dependencies.writeAlarmState(AlarmState.Idle)
            }
        } catch (error: Exception) {
            dependencies.recordDiagnostic("boot stateStore: ${error.message ?: error.javaClass.simpleName}")
        }

        dependencies.clearTestAlarmToken()
        dependencies.recoverNotificationListener()

        try {
            dependencies.recordBootEvent()
        } catch (error: Exception) {
            dependencies.recordDiagnostic("boot history: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}

private class AndroidBootReconcilerDependencies(
    context: Context,
    private val container: AppContainer
) : BootReconcilerDependencies {
    private val component = ComponentName(context, CameraNotificationListener::class.java)
    private val recovery = NotificationListenerRecovery(
        accessGranted = { container.readiness.snapshot().notificationAccessGranted },
        connectionState = container.listenerConnection,
        requestRebind = { NotificationListenerService.requestRebind(component) },
        recordDiagnostic = container.runtimeDiagnostics::record
    )

    override suspend fun awaitConfigLoaded() = container.awaitConfigLoaded()

    override suspend fun readAlarmState(): AlarmState = container.stateStore.read()

    override suspend fun writeAlarmState(state: AlarmState) = container.stateStore.write(state)

    override fun clearTestAlarmToken() {
        container.testAlarmToken.value = null
    }

    override suspend fun recoverNotificationListener(): ListenerRecoveryResult = recovery.recover()

    override suspend fun recordBootEvent() {
        val readiness = container.readiness.snapshot()
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
            details = "monitoring=${container.triggerConfiguration.monitoringEnabled}, " +
                "access=${readiness.notificationAccessGranted}, listener=${readiness.listenerStatus.name}"
        )
    }

    override fun recordDiagnostic(message: String) {
        container.runtimeDiagnostics.record(message)
    }
}
