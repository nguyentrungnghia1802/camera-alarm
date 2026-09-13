package com.personal.cameraalarm.trigger

import com.personal.cameraalarm.notification.IncomingNotification
import java.util.Locale

object NotificationNormalizer {
    private val whitespace = Regex("\\s+")
    fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(whitespace, " ").trim()
    fun normalize(notification: IncomingNotification): String = buildList {
        add(notification.title)
        add(notification.text)
        add(notification.bigText)
        addAll(notification.textLines)
        add(notification.subText)
    }.filterNotNull().filter { it.isNotBlank() }.joinToString("\n").let(::normalize)
}
