package com.personal.cameraalarm.boot

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.*

/** OS-backed repair only. Never starts an FGS/audio from a job or boot broadcast. */
class RecoveryJobs(private val context: Context) : PendingRecoveryScheduler {
    private val scheduler get() = context.getSystemService(JobScheduler::class.java)
    override fun arm(pending: AlarmState.Pending): Boolean = runCatching {
        val token = pending.trigger.alarmToken.value
        val existing = scheduler.allPendingJobs
        if (existing.any { it.extras.getString(TOKEN) == token }) return@runCatching true
        var id = 100_000 + (token.hashCode() and 0x3fffffff)
        while (existing.any { it.id == id }) id++
        val extras = PersistableBundle().apply { putString(TOKEN, token) }
        scheduler.schedule(JobInfo.Builder(id, ComponentName(context, RecoveryJobService::class.java))
            .setExtras(extras)
            .setMinimumLatency(PendingRecoveryPolicy.remainingMs(pending, System.currentTimeMillis(), SystemClock.elapsedRealtime()).coerceAtLeast(1))
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()) == JobScheduler.RESULT_SUCCESS
    }.getOrElse {
        AlarmTrace.record("RECOVERY_JOB_FAILED", pending.trigger.alarmToken, details = it.toString())
        false
    }

    override fun cancel(token: AlarmToken) {
        scheduler.allPendingJobs.filter { it.extras.getString(TOKEN) == token.value }
            .forEach { scheduler.cancel(it.id) }
    }

    fun boot(action: String, attempt: Int = 0): Boolean {
        if (attempt >= MAX_BOOT_ATTEMPTS) {
            AlarmTrace.record("BOOT_RECOVERY_EXHAUSTED", details = "action=$action attempts=$attempt")
            return false
        }
        val extras = PersistableBundle().apply { putString(ACTION, action); putInt(ATTEMPT, attempt) }
        val result = scheduler.schedule(JobInfo.Builder(BOOT_JOB_ID + attempt, ComponentName(context, RecoveryJobService::class.java))
            .setExtras(extras)
            .setMinimumLatency(if (attempt == 0) 1 else 30_000L * (1L shl (attempt - 1)))
            .build()) == JobScheduler.RESULT_SUCCESS
        if (!result) AlarmTrace.record("BOOT_RECOVERY_JOB_FAILED", details = "action=$action attempt=$attempt")
        return result
    }

    companion object {
        const val TOKEN = "pending_token"
        const val ACTION = "boot_action"
        const val ATTEMPT = "attempt"
        const val BOOT_JOB_ID = 7100
        const val MAX_BOOT_ATTEMPTS = 5
    }
}

class RecoveryJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as CameraAlarmApp
        running[params.jobId] = scope.launch {
            var retry = false
            try {
                val token = params.extras.getString(RecoveryJobs.TOKEN)
                if (token != null) {
                    retry = app.container.coordinator.recoverPending(AlarmToken(token))
                } else {
                    val action = params.extras.getString(RecoveryJobs.ACTION) ?: "RECOVERY_JOB"
                    val result = DefaultBootReconciler(this@RecoveryJobService, app.container, action).reconcile()
                    if (result.retry) app.container.recoveryJobs.boot(action, params.extras.getInt(RecoveryJobs.ATTEMPT) + 1)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AlarmTrace.record("RECOVERY_JOB_ERROR", details = error.toString())
                retry = true
            } finally {
                running.remove(params.jobId)
            }
            if (isActive) jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
