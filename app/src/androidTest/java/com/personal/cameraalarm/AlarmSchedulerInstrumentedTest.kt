package com.personal.cameraalarm

import android.app.PendingIntent
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.cameraalarm.alarm.AlarmReceiver
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.AndroidAlarmScheduler
import com.personal.cameraalarm.alarm.ScheduleResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerInstrumentedTest {
    @Test fun canonicalPendingIntentCanBeFoundAndCancelled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = AndroidAlarmScheduler(context)
        assertTrue("Exact-alarm access is required for this device test", scheduler.canScheduleExactAlarms())
        val token = AlarmToken("instrumented-test")
        try {
            assertEquals(ScheduleResult.Scheduled, scheduler.scheduleExact(token, System.currentTimeMillis() + 60_000))
            val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
            val existing = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            assertNotNull(existing)
        } finally { scheduler.cancel(token) }
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
        assertNull(PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
    }
}
