package com.personal.fakecamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNotificationPayloadTest {

    @Test
    fun testHumanDetectedPayload() {
        val payload = AlertPayload.HUMAN_DETECTED
        assertEquals("Human detected", payload.title)
        assertEquals("Front Door Camera detected a person", payload.text)
    }

    @Test
    fun testMotionDetectedPayload() {
        val payload = AlertPayload.MOTION_DETECTED
        assertEquals("Motion detected", payload.title)
        assertEquals("Front Door Camera detected movement", payload.text)
    }

    @Test
    fun testCameraOfflinePayload() {
        val payload = AlertPayload.CAMERA_OFFLINE
        assertEquals("Camera offline", payload.title)
        assertEquals("Front Door Camera connection lost", payload.text)
    }

    @Test
    fun testCustomPayload() {
        val payload = AlertPayload(
            title = "Glass break detected",
            text = "Living room window vibration sensor triggered"
        )
        assertEquals("Glass break detected", payload.title)
        assertEquals("Living room window vibration sensor triggered", payload.text)
    }

    @Test
    fun testNotificationIdGeneration() {
        val id1 = FakeNotificationHelper.nextNotificationId()
        val id2 = FakeNotificationHelper.nextNotificationId()
        assertTrue("Notification ID should auto-increment", id2 > id1)
    }
}
