package com.personal.cameraalarm.trigger

import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.schedule.ActiveScheduleGate
import com.personal.cameraalarm.schedule.ScheduleConfiguration
import com.personal.cameraalarm.schedule.ScheduleDecision
import com.personal.cameraalarm.util.Clock
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TriggerConfiguration(
    val monitoringEnabled: Boolean,
    val sourcePackage: String?,
    val rules: List<TriggerRule>,
    val scheduleConfiguration: ScheduleConfiguration = ScheduleConfiguration()
)

fun interface TriggerConfigurationSource { suspend fun current(): TriggerConfiguration }

enum class TriggerDecision {
    IGNORED_MONITORING_OFF, IGNORED_WRONG_PACKAGE, IGNORED_DUPLICATE, IGNORED_NO_RULE_MATCH,
    SCHEDULED, SCHEDULE_FAILED, SUPPRESSED_PENDING, SUPPRESSED_RINGING, SUPPRESSED_COOLDOWN,
    SUPPRESSED_OUTSIDE_ACTIVE_HOURS
}

fun interface TriggerHistory {
    suspend fun record(
        notification: IncomingNotification,
        decision: TriggerDecision,
        token: AlarmToken?,
        ruleId: String?
    )
}

private data class PipelineResult(
    val decision: TriggerDecision,
    val token: AlarmToken? = null,
    val ruleId: String? = null
)

class TriggerPipeline(
    private val clock: Clock,
    private val configuration: TriggerConfigurationSource,
    private val duplicates: DuplicateGuard,
    private val coordinator: ValidTriggerSink,
    private val history: TriggerHistory,
    private val historyFailure: (Throwable) -> Unit = {}
) {
    private val mutex = Mutex()

    suspend fun process(notification: IncomingNotification): TriggerDecision {
        val result = mutex.withLock { decide(notification) }
        val decision = result.decision
        if (decision.shouldPersistHistory()) {
            try {
                history.record(notification, decision, result.token, result.ruleId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                historyFailure(error)
            }
        }
        return decision
    }

    private suspend fun decide(notification: IncomingNotification): PipelineResult {
        val config = configuration.current()
        if (!config.monitoringEnabled) return PipelineResult(TriggerDecision.IGNORED_MONITORING_OFF)
        val allowedPackages = config.rules.asSequence()
            .filter { it.enabled }
            .map { it.sourcePackage }
            .filter(String::isNotBlank)
            .toSet()
        if (notification.packageName !in allowedPackages)
            return PipelineResult(TriggerDecision.IGNORED_WRONG_PACKAGE)

        val searchable = NotificationNormalizer.normalize(notification)
        val rule = TriggerMatcher.match(notification.packageName, searchable, config.rules)
            ?: return PipelineResult(TriggerDecision.IGNORED_NO_RULE_MATCH)
        try {
            AlarmTrace.record("TRIGGER_MATCHED", AlarmToken(notification.traceToken),
                details = "rule=${rule.id} package=${notification.packageName}")
        } catch (_: Throwable) {}

        val eventTime = notification.postTimeEpochMs.takeIf { it > 0 } ?: clock.nowEpochMs()
        val scheduleDecision = ActiveScheduleGate.evaluate(config.scheduleConfiguration, eventTime)
        if (scheduleDecision == ScheduleDecision.OUTSIDE_ACTIVE_HOURS) {
            return PipelineResult(TriggerDecision.SUPPRESSED_OUTSIDE_ACTIVE_HOURS, ruleId = rule.id)
        }

        val key = notification.key.ifBlank { fallbackKey(notification, searchable) }
        val now = clock.nowEpochMs()
        if (duplicates.isDuplicate(key, now)) {
            return PipelineResult(TriggerDecision.IGNORED_DUPLICATE, ruleId = rule.id)
        }
        duplicates.markSeen(key, now)

        val token = AlarmToken(notification.traceToken)
        val trigger = TriggerSnapshot(token, notification.packageName, key, rule.id, notification.title?.take(300), searchable.take(300), now)
        val outcome = coordinator.onValidTrigger(trigger)
        return PipelineResult(TriggerDecision.valueOf(outcome.name), token, rule.id)
    }

    private fun fallbackKey(n: IncomingNotification, normalized: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val hash = digest.take(8).joinToString("") { "%02x".format(it) }
        return "${n.packageName}:${n.notificationId}:${n.tag ?: ""}:${n.postTimeEpochMs / 10_000}:$hash"
    }
}

private fun TriggerDecision.shouldPersistHistory(): Boolean =
    this != TriggerDecision.IGNORED_WRONG_PACKAGE &&
        this != TriggerDecision.IGNORED_MONITORING_OFF
