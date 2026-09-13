package com.personal.cameraalarm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.cameraalarm.data.AppDatabase
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.rule.TriggerRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun triggerRuleDaoCrud() = runBlocking {
        val rule = TriggerRuleEntity(
            id = "test-rule",
            name = "Person Detection",
            enabled = true,
            sourcePackage = "com.camera.test",
            matchMode = "CONTAINS_ANY",
            keywordsJson = "[\"person\",\"human\"]",
            priority = 1,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        db.triggerRuleDao().insertOrUpdate(rule)
        val loaded = db.triggerRuleDao().getById("test-rule")
        assertNotNull(loaded)
        assertEquals("Person Detection", loaded?.name)

        val all = db.triggerRuleDao().observeAll().first()
        assertEquals(1, all.size)

        db.triggerRuleDao().deleteById("test-rule")
        assertNull(db.triggerRuleDao().getById("test-rule"))
    }

    @Test
    fun alertEventDaoRetentionLimit() = runBlocking {
        // Insert 510 events
        for (i in 1..510) {
            val event = AlertEventEntity(
                createdAtEpochMs = i.toLong(),
                sourcePackage = "com.camera.test",
                notificationKey = "key-$i",
                title = "Event $i",
                textPreview = "Preview $i",
                normalizedHash = null,
                decision = "SCHEDULED",
                ruleId = "rule-1",
                alarmToken = "token-$i",
                details = null
            )
            db.alertEventDao().insert(event)
        }
        assertEquals(510, db.alertEventDao().count())

        // Prune retention
        db.alertEventDao().pruneOverRetention()

        // Count should now be exactly 500
        assertEquals(500, db.alertEventDao().count())

        // The oldest 10 (timestamps 1..10) should have been pruned
        val remaining = db.alertEventDao().observeAll().first()
        assertEquals(500, remaining.size)
        // Highest timestamp should be 510, lowest should be 11
        assertEquals(510L, remaining.first().createdAtEpochMs)
        assertEquals(11L, remaining.last().createdAtEpochMs)
    }
}
