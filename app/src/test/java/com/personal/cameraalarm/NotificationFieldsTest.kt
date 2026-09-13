package com.personal.cameraalarm

import com.personal.cameraalarm.notification.NotificationFields
import org.junit.Assert.*
import org.junit.Test

class NotificationFieldsTest {
    @Test fun convertsCharSequencesAndIgnoresWrongTypes() {
        val fields = NotificationFields.fromValues(StringBuilder("Title"), 42, "Big", arrayOf("one", 7, StringBuilder("two")), null)
        assertEquals("Title", fields.title)
        assertNull(fields.text)
        assertEquals("Big", fields.bigText)
        assertEquals(listOf("one", "two"), fields.textLines)
        assertNull(fields.subText)
        assertEquals(emptyList<String>(), NotificationFields.fromValues(null, null, null, "bad", null).textLines)
    }
}
