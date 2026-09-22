package com.personal.cameraalarm

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.alarm.*
import com.personal.cameraalarm.util.Clock
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BootStateStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val trigger = TriggerSnapshot(AlarmToken("old"), "camera", "key", "rule", null, null, 10)

    @Test fun realDataStoreInvalidatesAllPreviousBootStatesAndKeepsCurrentBootPending() = runBlocking {
        val file = File(context.cacheDir, "boot-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = scope) { file }
        var boot = 20
        val store = DataStoreAlarmStateStore(context, data, { boot }, "owner")
        val scheduler = object : AlarmScheduler {
            override fun canScheduleExactAlarms() = true
            override fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long) = ScheduleResult.Scheduled
            override fun cancel(token: AlarmToken) = Unit
        }
        try {
            for (state in listOf(AlarmState.Pending(trigger, 1000, 1000), AlarmState.Ringing(trigger, 10), AlarmState.Cooldown(10000, trigger.alarmToken))) {
                store.write(state)
                boot++
                val coordinator = AlarmCoordinator(Clock { 100 }, scheduler, store, { AlarmPolicy() })
                coordinator.reconcile()
                assertEquals(AlarmState.Idle, store.read())
                assertEquals(store.read(), coordinator.state.value)
                assertTrue(store.restoreNote!!.contains("invalidated=true"))
            }
            val c = AlarmCoordinator(Clock { 100 }, scheduler, store, { AlarmPolicy() })
            c.onValidTrigger(trigger.copy(alarmToken = AlarmToken("new")))
            val pending = store.read()
            coroutineScope { repeat(10) { launch(Dispatchers.Default) { c.reconcile() } } }
            assertEquals(pending, store.read())
            assertEquals(pending, c.state.value)
            val recreated = DataStoreAlarmStateStore(context, data, { boot }, "next-owner")
            assertEquals(pending, recreated.read())
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            file.delete()
        }
    }

    @Test fun currentSnapshotTransactionCannotEraseNewBootWrite() = runBlocking {
        val file = File(context.cacheDir, "race-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = scope) { file }
        var boot = 1
        val first = DataStoreAlarmStateStore(context, data, { boot }, "same-owner")
        val second = DataStoreAlarmStateStore(context, data, { boot }, "same-owner")
        try {
            repeat(30) {
                first.write(AlarmState.Pending(trigger, 1000))
                boot++
                val current = AlarmState.Pending(trigger.copy(alarmToken = AlarmToken("new-$boot")), 1000)
                coroutineScope {
                    launch(Dispatchers.Default) { first.read() }
                    launch(Dispatchers.Default) { second.write(current) }
                }
                assertEquals(current, first.read())
            }
        } finally {
            scope.cancel(); scope.coroutineContext[Job]!!.join(); file.delete()
        }
    }
}
