package com.personal.cameraalarm.notification

data class IncomingNotification(
    val key: String,
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
    val postTimeEpochMs: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>,
    val subText: String?,
    val traceToken: String = java.util.UUID.randomUUID().toString()
)

/** Only allowlisted metadata; notification keys embed app-controlled private tags. */
fun IncomingNotification.safeTraceDetails(): String = "package=$packageName id=$notificationId"
