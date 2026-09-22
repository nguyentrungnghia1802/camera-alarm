package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.AlarmEffect
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.TriggerSnapshot
import com.personal.cameraalarm.data.history.AlarmHistoryEventFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmHistoryEventFactoryTest {
    private val trigger = TriggerSnapshot(
        alarmToken = AlarmToken("alarm-token"),
        sourcePackage = "camera.source",
        notificationKey = "notification-key",
        ruleId = "rule-v2",
        title = "Person detected",
        textPreview = "Front door",
        receivedAtEpochMs = 100L
    )

    @Test
    fun lifecycleEventsKeepTheMatchedRuleAndAlarmToken() {
        val effects = listOf(
            AlarmEffect.StartRinging(trigger) to "ALARM_FIRED",
            AlarmEffect.StopRuntime(trigger) to "ALARM_STOPPED",
            AlarmEffect.CancelExact(trigger) to "ALARM_CANCELLED",
            AlarmEffect.RecordFailure("permission denied", trigger) to "SCHEDULE_FAILED"
        )

        effects.forEach { (effect, decision) ->
            val event = AlarmHistoryEventFactory.fromEffect(effect, 500L)
            requireNotNull(event)
            assertEquals(decision, event.decision)
            assertEquals("rule-v2", event.ruleId)
            assertEquals("alarm-token", event.alarmToken)
            assertEquals("camera.source", event.sourcePackage)
            assertEquals("notification-key", event.notificationKey)
            assertEquals("Person detected", event.title)
            assertEquals("Front door", event.textPreview)
        }
    }

    @Test
    fun nonLifecycleEffectsDoNotCreateDuplicateHistoryRows() {
        assertNull(
            AlarmHistoryEventFactory.fromEffect(
                AlarmEffect.ScheduleExact(trigger, 1_000L),
                500L
            )
        )
        assertNull(
            AlarmHistoryEventFactory.fromEffect(
                AlarmEffect.RecordSuppression(com.personal.cameraalarm.alarm.SuppressionReason.PENDING),
                500L
            )
        )
    }
}
