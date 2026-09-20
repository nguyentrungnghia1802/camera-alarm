package com.personal.cameraalarm.boot

import com.personal.cameraalarm.notification.ListenerConnectionState
import com.personal.cameraalarm.notification.ListenerStatus
import kotlinx.coroutines.delay

enum class ListenerRecoveryResult {
    ACCESS_DENIED,
    ALREADY_CONNECTED,
    CONNECTED,
    DISCONNECTED
}

class NotificationListenerRecovery(
    private val accessGranted: () -> Boolean,
    private val connectionState: ListenerConnectionState,
    private val requestRebind: () -> Unit,
    private val recordDiagnostic: (String) -> Unit,
    private val retryDelayMs: Long = 750L,
    private val maxAttempts: Int = 3,
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    init {
        require(maxAttempts > 0)
        require(retryDelayMs >= 0)
    }

    suspend fun recover(): ListenerRecoveryResult {
        if (!accessGranted()) {
            connectionState.disconnected()
            recordDiagnostic("listener recovery: access denied")
            return ListenerRecoveryResult.ACCESS_DENIED
        }
        if (connectionState.status.value == ListenerStatus.CONNECTED) {
            recordDiagnostic("listener recovery: already connected")
            return ListenerRecoveryResult.ALREADY_CONNECTED
        }

        connectionState.reconnecting()
        repeat(maxAttempts) { index ->
            val attempt = index + 1
            try {
                requestRebind()
                recordDiagnostic("listener recovery: rebind requested attempt=$attempt")
            } catch (error: Exception) {
                recordDiagnostic(
                    "listener recovery: request failed attempt=$attempt: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }

            if (connectionState.status.value == ListenerStatus.CONNECTED) {
                return ListenerRecoveryResult.CONNECTED
            }
            if (attempt < maxAttempts) {
                wait(retryDelayMs)
                if (connectionState.status.value == ListenerStatus.CONNECTED) {
                    return ListenerRecoveryResult.CONNECTED
                }
            }
        }

        connectionState.disconnected()
        recordDiagnostic("listener recovery: disconnected after $maxAttempts attempts")
        return ListenerRecoveryResult.DISCONNECTED
    }
}
