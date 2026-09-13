package com.personal.cameraalarm.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

data class NotificationFields(
    val title: String?, val text: String?, val bigText: String?, val textLines: List<String>, val subText: String?
) {
    companion object {
        fun fromValues(title: Any?, text: Any?, bigText: Any?, textLines: Any?, subText: Any?) = NotificationFields(
            title.asText(), text.asText(), bigText.asText(),
            (textLines as? Array<*>)?.mapNotNull { it.asText() } ?: emptyList(), subText.asText()
        )
        private fun Any?.asText(): String? = (this as? CharSequence)?.toString()
    }
}

object NotificationExtractor {
    fun from(sbn: StatusBarNotification): IncomingNotification {
        val extras = sbn.notification?.extras
        val fields = NotificationFields.fromValues(
            extras.safeGet(Notification.EXTRA_TITLE), extras.safeGet(Notification.EXTRA_TEXT),
            extras.safeGet(Notification.EXTRA_BIG_TEXT), extras.safeGet(Notification.EXTRA_TEXT_LINES),
            extras.safeGet(Notification.EXTRA_SUB_TEXT)
        )
        return IncomingNotification(sbn.key.orEmpty(), sbn.packageName.orEmpty(), sbn.id, sbn.tag, sbn.postTime,
            fields.title, fields.text, fields.bigText, fields.textLines, fields.subText)
    }
    @Suppress("DEPRECATION")
    private fun Bundle?.safeGet(key: String): Any? = try { this?.get(key) } catch (_: RuntimeException) { null }
}
