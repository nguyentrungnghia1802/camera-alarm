package com.personal.cameraalarm

import com.personal.cameraalarm.notification.IncomingNotification
import com.personal.cameraalarm.notification.safeTraceDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTracePrivacyTest {
    @Test fun traceExcludesAllAppControlledPayloadAndKeyFields() {
        val incoming = IncomingNotification("private-key", "unrelated.app", 7,
            "private-tag", 0, "private-title", "private-text", "private-big",
            listOf("private-line"), "private-subtext")
        assertEquals("package=unrelated.app id=7", incoming.safeTraceDetails())
    }
}
