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
    val subText: String?
)
