package com.personal.cameraalarm.alarm

import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

interface AlarmStateStore {
    suspend fun read(): AlarmState
    suspend fun write(state: AlarmState)
    val restoreNote: String? get() = null
}
class InMemoryAlarmStateStore : AlarmStateStore {
    private var stored: AlarmState = AlarmState.Idle
    override suspend fun read() = stored
    override suspend fun write(state: AlarmState) { stored = state }
}
fun interface AlarmEffectObserver { suspend fun onEffect(effect: AlarmEffect) }
enum class AlarmOutcome { SCHEDULED, SCHEDULE_FAILED, SUPPRESSED_PENDING, SUPPRESSED_RINGING, SUPPRESSED_COOLDOWN }
fun interface ValidTriggerSink { suspend fun onValidTrigger(trigger: TriggerSnapshot): AlarmOutcome }
data class AlarmReconciliation(val previous: String, val current: AlarmState)

/** All production state changes, delivery claims and runtime starts share this owner. */
class AlarmCoordinator(
    private val clock: Clock,
    private val scheduler: AlarmScheduler,
    private val store: AlarmStateStore,
    private val policy: () -> AlarmPolicy,
    private val observer: AlarmEffectObserver = AlarmEffectObserver { },
    private val recoveryScheduler: PendingRecoveryScheduler = NoPendingRecoveryScheduler,
    private val elapsedMs: () -> Long = { clock.nowEpochMs() },
    private val newToken: () -> AlarmToken = { AlarmToken(UUID.randomUUID().toString()) },
    private val trace: (String, TriggerSnapshot?, String) -> Unit = { _, _, _ -> }
) : ValidTriggerSink {
    private val mutex = Mutex()
    private var hydrated = false
    private val mutableState = MutableStateFlow<AlarmState>(AlarmState.Idle)
    val state = mutableState.asStateFlow()
    private val mutableInitialized = MutableStateFlow(false)
    val initialized = mutableInitialized.asStateFlow()

    // Cancellation cannot split persist/register or retire/cancel. Death is handled by hydration/jobs.
    private suspend fun <T> owned(block: suspend () -> T): T = mutex.withLock {
        withContext(NonCancellable) { block() }
    }

    private suspend fun publish(next: AlarmState) {
        store.write(next)
        mutableState.value = next
    }

    private suspend fun load(): AlarmState {
        val current = store.read()
        mutableState.value = current
        if (!hydrated) {
            hydrated = true
            try {
                if (current is AlarmState.Pending) {
                    if (!scheduler.canScheduleExactAlarms() || recoveryRemainingMs(current) <= 0) {
                        recoverLocked(current)
                    } else {
                        // Pending is intent, not proof of OS registration. Re-register idempotently.
                        register(current)
                    }
                }
                mutableInitialized.value = true
            } catch (error: Exception) {
                hydrated = false
                throw error
            }
        }
        return mutableState.value
    }

    suspend fun reconcile(): AlarmReconciliation = owned {
        load()
        val previous = store.restoreNote ?: mutableState.value.javaClass.simpleName
        (mutableState.value as? AlarmState.Pending)?.let { recoverLocked(it) }
        AlarmReconciliation(previous, mutableState.value)
    }

    suspend fun snapshot(): AlarmState = owned { load() }

    override suspend fun onValidTrigger(trigger: TriggerSnapshot): AlarmOutcome = owned {
        load()
        (mutableState.value as? AlarmState.Pending)?.let { recoverLocked(it) }
        val current = mutableState.value
        val activePolicy = policy()
        val transition = AlarmReducer.reduce(current, AlarmEvent.ValidTrigger(trigger), clock.nowEpochMs(), activePolicy)
        val suppression = transition.effects.filterIsInstance<AlarmEffect.RecordSuppression>().firstOrNull()
        if (suppression != null) {
            if (current is AlarmState.Pending) trace("SUPPRESSED_PENDING", current.trigger,
                "rejected_token=${trigger.alarmToken.value} age_ms=${clock.nowEpochMs() - current.trigger.receivedAtEpochMs} " +
                    "recovery_in_ms=${recoveryRemainingMs(current)} attempt=${current.recoveryAttempt}")
            observer.onEffect(suppression)
            return@owned when (suppression.reason) {
                SuppressionReason.PENDING -> AlarmOutcome.SUPPRESSED_PENDING
                SuppressionReason.RINGING -> AlarmOutcome.SUPPRESSED_RINGING
                else -> AlarmOutcome.SUPPRESSED_COOLDOWN
            }
        }
        val pending = transition.nextState as AlarmState.Pending
        val scheduled = pending.copy(
            trigger = trigger.copy(deadlineEpochMs = pending.scheduledAtEpochMs, configuredDelayMs = activePolicy.delayMs),
            deadlineElapsedMs = elapsedMs() + (pending.scheduledAtEpochMs - clock.nowEpochMs()).coerceAtLeast(0)
        )
        if (register(scheduled)) AlarmOutcome.SCHEDULED else AlarmOutcome.SCHEDULE_FAILED
    }

    private suspend fun register(pending: AlarmState.Pending): Boolean {
        if (!scheduler.canScheduleExactAlarms()) {
            retire(pending, "Exact alarm permission missing")
            return false
        }
        if (!recoveryScheduler.arm(pending)) {
            retire(pending, "Pending recovery job registration failed")
            return false
        }
        publish(pending)
        trace("ALARM_PENDING_CREATED", pending.trigger, "attempt=${pending.recoveryAttempt}")
        val result = scheduler.scheduleExact(pending.trigger.alarmToken, pending.scheduledAtEpochMs)
        if (result != ScheduleResult.Scheduled) {
            retire(pending, "Exact alarm schedule failed: $result")
            return false
        }
        trace("EXACT_ALARM_SCHEDULED", pending.trigger, "attempt=${pending.recoveryAttempt}")
        observer.onEffect(AlarmEffect.ScheduleExact(pending.trigger, pending.scheduledAtEpochMs))
        return true
    }

    fun recoveryRemainingMs(pending: AlarmState.Pending): Long =
        PendingRecoveryPolicy.remainingMs(pending, clock.nowEpochMs(), elapsedMs())

    /** True only if this token is still pending (an early JobScheduler invocation should retry). */
    suspend fun recoverPending(token: AlarmToken): Boolean = owned {
        load()
        val current = mutableState.value
        if (current is AlarmState.Pending && current.trigger.alarmToken == token) recoverLocked(current)
        (mutableState.value as? AlarmState.Pending)?.trigger?.alarmToken == token
    }

    private suspend fun recoverLocked(pending: AlarmState.Pending) {
        if (!scheduler.canScheduleExactAlarms()) {
            retire(pending, "Exact alarm permission revoked")
        } else if (recoveryRemainingMs(pending) <= 0) {
            retire(pending, "Pending overdue; attempt=${pending.recoveryAttempt}")
            if (pending.recoveryAttempt < PendingRecoveryPolicy.MAX_RETRIES) {
                val now = clock.nowEpochMs()
                val replacement = pending.copy(
                    trigger = pending.trigger.copy(alarmToken = newToken(), deadlineEpochMs = now),
                    scheduledAtEpochMs = now, deadlineElapsedMs = elapsedMs(),
                    recoveryAttempt = pending.recoveryAttempt + 1
                )
                trace("PENDING_RECOVERY", replacement.trigger, "replaces=${pending.trigger.alarmToken.value}")
                register(replacement)
            }
        }
    }

    private suspend fun retire(pending: AlarmState.Pending, reason: String) {
        // Invalidate BEFORE cancellation. Already-dispatched callbacks are now stale.
        publish(AlarmState.Idle)
        scheduler.cancel(pending.trigger.alarmToken)
        recoveryScheduler.cancel(pending.trigger.alarmToken)
        trace("PENDING_RETIRED", pending.trigger, reason)
        observer.onEffect(AlarmEffect.RecordFailure(reason, pending.trigger))
    }

    suspend fun onExactAlarmFired(trigger: TriggerSnapshot) { claimAlarm(trigger.alarmToken) { } }

    suspend fun claimAlarm(token: AlarmToken, requestRuntime: (TriggerSnapshot) -> Unit): Boolean = owned {
        load()
        val current = mutableState.value
        if (current !is AlarmState.Pending || current.trigger.alarmToken != token) {
            trace("STALE_ALARM", null, "token=${token.value}")
            return@owned false
        }
        if (!scheduler.canScheduleExactAlarms()) {
            retire(current, "Permission missing at delivery")
            return@owned false
        }
        trace("ALARM_RECEIVER_FIRED", current.trigger, "attempt=${current.recoveryAttempt}")
        // If delivery wins the lock before recovery, accept once, including late delivery.
        publish(AlarmState.Ringing(current.trigger, clock.nowEpochMs()))
        scheduler.cancel(token)
        recoveryScheduler.cancel(token)
        trace("RINGING", current.trigger, "attempt=${current.recoveryAttempt}")
        observer.onEffect(AlarmEffect.StartRinging(current.trigger))
        try {
            requestRuntime(current.trigger)
        } catch (error: Exception) {
            publish(AlarmState.Idle)
            observer.onEffect(AlarmEffect.RecordFailure("Runtime dispatch failed: ${error.message}", current.trigger))
            throw error
        }
        true
    }

    /** Revalidate after asynchronous service delivery, before touching audio. Called on service Main. */
    suspend fun withRingingOwnership(token: AlarmToken, start: (TriggerSnapshot) -> Unit): Boolean = owned {
        val current = load()
        if (current !is AlarmState.Ringing || current.trigger.alarmToken != token) return@owned false
        start(current.trigger)
        true
    }

    suspend fun withTestOwnership(start: () -> Unit): Boolean = owned {
        val current = load()
        if (current is AlarmState.Pending || current is AlarmState.Ringing) return@owned false
        start()
        true
    }

    suspend fun onStopRequested(token: AlarmToken?) = owned {
        val current = load()
        val transition = AlarmReducer.reduce(current, AlarmEvent.StopRequested(token), clock.nowEpochMs(), policy())
        if (transition.nextState != current) publish(transition.nextState)
        for (effect in transition.effects) {
            if (effect is AlarmEffect.CancelExact) {
                scheduler.cancel(effect.trigger.alarmToken)
                recoveryScheduler.cancel(effect.trigger.alarmToken)
            }
            observer.onEffect(effect)
        }
    }

    suspend fun resetCooldown() = owned {
        if (load() is AlarmState.Cooldown) publish(AlarmState.Idle)
    }
}
