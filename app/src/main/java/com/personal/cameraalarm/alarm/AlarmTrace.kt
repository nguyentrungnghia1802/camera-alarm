package com.personal.cameraalarm.alarm

import android.os.SystemClock
import android.util.Log
import java.util.UUID

object AlarmTrace {
    val session: String = UUID.randomUUID().toString()
    fun record(stage: String, token: AlarmToken? = null, trigger: TriggerSnapshot? = null, details: String = "") {
        val now = System.currentTimeMillis()
        val deadline = trigger?.deadlineEpochMs
        Log.i("CameraAlarm", "$stage token=${token?.value ?: trigger?.alarmToken?.value} " +
            "epoch_ms=$now elapsed_ms=${SystemClock.elapsedRealtime()} session=$session " +
            "deadline_ms=$deadline delay_ms=${trigger?.configuredDelayMs} " +
            "overdue_ms=${deadline?.let { (now - it).coerceAtLeast(0) }} $details")
    }
}
