package com.personal.cameraalarm.boot

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.notification.CameraNotificationListener
import kotlinx.coroutines.CancellationException

data class BootRecoveryOutcome(
    val configLoaded: Boolean,
    val reconciliation: AlarmReconciliation?,
    val listener: ListenerRecoveryResult?,
    val monitoring: Boolean,
    val exact: Boolean,
    val error: String? = null
) {
    val success get() = configLoaded && reconciliation != null && error == null &&
        (!monitoring || exact) && listener in setOf(ListenerRecoveryResult.CONNECTED, ListenerRecoveryResult.ALREADY_CONNECTED)
    val retry get() = !configLoaded || reconciliation == null || error != null ||
        (monitoring && listener == ListenerRecoveryResult.DISCONNECTED)
}
interface BootReconciler { suspend fun reconcile(): BootRecoveryOutcome }
internal interface BootReconcilerDependencies {
    suspend fun awaitConfigLoaded(): Boolean
    suspend fun reconcileAlarm(): AlarmReconciliation
    fun monitoringEnabled(): Boolean
    fun exactAlarmCapable(): Boolean
    suspend fun recoverNotificationListener(): ListenerRecoveryResult
    suspend fun recordBootEvent(outcome: BootRecoveryOutcome)
    fun recordDiagnostic(message: String)
}
class DefaultBootReconciler internal constructor(private val dependencies: BootReconcilerDependencies) : BootReconciler {
    constructor(context: Context, container: AppContainer, action: String = "BOOT_COMPLETED") :
        this(AndroidBootReconcilerDependencies(context, container, action))

    override suspend fun reconcile(): BootRecoveryOutcome {
        var config = false
        var reconciliation: AlarmReconciliation? = null
        var listener: ListenerRecoveryResult? = null
        var error: String? = null
        try {
            config = dependencies.awaitConfigLoaded()
            // Runtime invalidation/hydration does not depend on monitoring config or listener connection.
            reconciliation = dependencies.reconcileAlarm()
            listener = dependencies.recoverNotificationListener()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            dependencies.recordDiagnostic("boot recovery: $error")
        }
        val outcome = BootRecoveryOutcome(config, reconciliation, listener,
            dependencies.monitoringEnabled(), dependencies.exactAlarmCapable(), error)
        try {
            dependencies.recordBootEvent(outcome)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            dependencies.recordDiagnostic("boot history: ${failure.message}")
        }
        return outcome
    }
}
private class AndroidBootReconcilerDependencies(
    private val context: Context,
    private val container: AppContainer,
    private val action: String
) : BootReconcilerDependencies {
    private val recovery = NotificationListenerRecovery(
        accessGranted = { container.readiness.snapshot().notificationAccessGranted },
        connectionState = container.listenerConnection,
        requestRebind = { NotificationListenerService.requestRebind(ComponentName(context, CameraNotificationListener::class.java)) },
        recordDiagnostic = container.runtimeDiagnostics::record
    )
    override suspend fun awaitConfigLoaded() = container.awaitConfigLoaded()
    override suspend fun reconcileAlarm() = container.coordinator.reconcile()
    override fun monitoringEnabled() = container.triggerConfiguration.monitoringEnabled
    override fun exactAlarmCapable() = container.exactAlarmAccess.isGranted()
    override suspend fun recoverNotificationListener() = recovery.recover()
    override suspend fun recordBootEvent(outcome: BootRecoveryOutcome) {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        val decision = if (outcome.success) "BOOT_RECONCILED" else "BOOT_RECOVERY_INCOMPLETE"
        val details = "boot=$boot session=${AlarmTrace.session} action=$action " +
            "previous=${outcome.reconciliation?.previous} reconciled=${outcome.reconciliation?.current?.javaClass?.simpleName} " +
            "monitoring=${outcome.monitoring} listener=${container.listenerConnection.status.value} " +
            "exact=${outcome.exact} config=${outcome.configLoaded} result=${outcome.listener} error=${outcome.error}"
        AlarmTrace.record(decision, details = details)
        container.historyRepository.recordEvent(System.currentTimeMillis(), container.triggerConfiguration.sourcePackage,
            null, "System recovery", if (outcome.success) "Recovery completed" else "Recovery incomplete",
            null, decision, null, null, details)
    }
    override fun recordDiagnostic(message: String) { container.runtimeDiagnostics.record(message) }
}
