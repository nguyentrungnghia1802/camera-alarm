package com.personal.cameraalarm.alarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A delivery grace, not an extra delay on the normal alarm path. */
object PendingRecoveryPolicy {
    // AOSP applies a 5 s minimum futurity even to short alarm-clock requests.
    // Allow that floor plus 5 s delivery/startup margin, including an immediate retry.
    const val GRACE_MS = 10_000L
    const val MAX_RETRIES = 1
    fun remainingMs(pending: AlarmState.Pending, epochMs: Long, elapsedMs: Long): Long =
        (pending.deadlineElapsedMs?.minus(elapsedMs)
            ?: (pending.scheduledAtEpochMs - epochMs)) + GRACE_MS
}

interface PendingRecoveryScheduler {
    /** Registered before persisting Pending, to cover death before AlarmManager registration. */
    fun arm(pending: AlarmState.Pending): Boolean
    fun cancel(token: AlarmToken)
}
object NoPendingRecoveryScheduler : PendingRecoveryScheduler {
    override fun arm(pending: AlarmState.Pending) = true
    override fun cancel(token: AlarmToken) = Unit
}

fun AlarmCoordinator.watchPending(scope: CoroutineScope) = scope.launch {
    state.collectLatest { current ->
        if (current is AlarmState.Pending) {
            delay(recoveryRemainingMs(current).coerceAtLeast(0))
            // Independent child: publishing state must not cancel recovery halfway through.
            scope.launch {
                try { recoverPending(current.trigger.alarmToken) }
                catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    AlarmTrace.record("PENDING_RECOVERY_ERROR", current.trigger.alarmToken, details = error.toString())
                    // OS job remains armed; it can retry after transient persistence failures.
                }
            }
        }
    }
}
