package com.personal.cameraalarm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.cameraalarm.data.AppDatabase
import com.personal.cameraalarm.data.history.AlertEventDao
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.history.HistoryRepository
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
        db.triggerRuleDao().insert(rule)
        val loaded = db.triggerRuleDao().getById("test-rule")
        assertNotNull(loaded)
        assertEquals("Person Detection", loaded?.name)

        val all = db.triggerRuleDao().observeAll().first()
        assertEquals(1, all.size)

        db.triggerRuleDao().deleteById("test-rule")
        assertNull(db.triggerRuleDao().getById("test-rule"))
    }

    @Test
    fun alertEventDaoPrunesByMaximumCountAndKeepsPaginationStable() = runBlocking {
        val dao = db.alertEventDao()
        for (i in 1..120) dao.insert(event(i))

        dao.pruneRetention(
            cutoffEpochMs = Long.MIN_VALUE,
            maxRetained = HistoryRepository.MAX_RETAINED,
            maxSuppressed = HistoryRepository.MAX_SUPPRESSED_RETAINED
        )

        assertEquals(100, dao.count())
        val newestPage = dao.observePaged("ALL", limit = 25, offset = 0).first()
        val oldestPage = dao.observePaged("ALL", limit = 25, offset = 75).first()
        val beyondRetention = dao.observePaged("ALL", limit = 25, offset = 100).first()
        assertEquals(120L, newestPage.first().createdAtEpochMs)
        assertEquals(21L, oldestPage.last().createdAtEpochMs)
        assertTrue(beyondRetention.isEmpty())
    }

    @Test
    fun alertEventDaoPrunesEventsOutsideRetentionWindow() = runBlocking {
        val dao = db.alertEventDao()
        val now = 10_000_000_000L
        val cutoff = now - HistoryRepository.RETENTION_WINDOW_MS
        dao.insert(event(1, createdAtEpochMs = cutoff - 1))
        dao.insert(event(2, createdAtEpochMs = cutoff))
        dao.insert(event(3, createdAtEpochMs = now))

        dao.pruneRetention(cutoff)

        val remaining = dao.observeAll().first()
        assertEquals(listOf(now, cutoff), remaining.map { it.createdAtEpochMs })
    }

    @Test
    fun alertEventDaoCapsSuppressedEventsWithoutRemovingTriggeredEvents() = runBlocking {
        val dao = db.alertEventDao()
        for (i in 1..15) dao.insert(event(i, decision = "SUPPRESSED_COOLDOWN"))
        for (i in 16..20) dao.insert(event(i, decision = "SCHEDULED"))

        dao.pruneRetention(Long.MIN_VALUE)

        val all = dao.observeAll().first()
        assertEquals(15, all.size)
        assertEquals(10, all.count { it.decision == "SUPPRESSED_COOLDOWN" })
        assertEquals(5, all.count { it.decision == "SCHEDULED" })
        assertEquals(6L, all.filter { it.decision == "SUPPRESSED_COOLDOWN" }.minOf { it.createdAtEpochMs })
    }

    private fun event(
        index: Int,
        createdAtEpochMs: Long = index.toLong(),
        decision: String = "SCHEDULED"
    ) = AlertEventEntity(
        createdAtEpochMs = createdAtEpochMs,
        sourcePackage = "com.camera.test",
        notificationKey = "key-$index",
        title = "Event $index",
        textPreview = "Preview $index",
        normalizedHash = null,
        decision = decision,
        ruleId = "rule-1",
        alarmToken = "token-$index",
        details = null
    )
}
