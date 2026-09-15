package com.personal.cameraalarm

import com.personal.cameraalarm.data.history.AlertEventDao
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.history.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FakeAlertEventDao : AlertEventDao {
    private val items = mutableListOf<AlertEventEntity>()
    private val flow = MutableStateFlow<List<AlertEventEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedByDescending { it.createdAtEpochMs }
    }

    override fun observeAll(): Flow<List<AlertEventEntity>> = flow

    override fun observePaged(filter: String, limit: Int, offset: Int): Flow<List<AlertEventEntity>> {
        return flow.map { all ->
            val filtered = applyFilter(all, filter)
            filtered.drop(offset).take(limit)
        }
    }

    override fun observeCount(filter: String): Flow<Int> {
        return flow.map { all -> applyFilter(all, filter).size }
    }

    private fun applyFilter(all: List<AlertEventEntity>, filter: String): List<AlertEventEntity> {
        return when (filter) {
            "ALL" -> all
            "TRIGGERED" -> all.filter { it.decision == "SCHEDULED" || it.decision == "ALARM_FIRED" }
            "SUPPRESSED" -> all.filter { it.decision.startsWith("SUPPRESSED_") || it.decision.startsWith("IGNORED_") }
            "ERRORS" -> all.filter { it.decision == "SCHEDULE_FAILED" || it.decision == "ALARM_RUNTIME_ERROR" }
            else -> all
        }
    }

    override suspend fun insert(event: AlertEventEntity): Long {
        val id = nextId++
        val entity = event.copy(id = id)
        items.add(entity)
        emit()
        return id
    }

    override suspend fun count(): Int = items.size

    override suspend fun deleteOlderThan(cutoffEpochMs: Long): Int {
        val countBefore = items.size
        items.removeAll { it.createdAtEpochMs < cutoffEpochMs }
        emit()
        return countBefore - items.size
    }

    override suspend fun deleteExcess(maxRetained: Int): Int {
        if (items.size <= maxRetained) return 0
        items.sortByDescending { it.createdAtEpochMs }
        val toKeep = items.take(maxRetained).toSet()
        val countBefore = items.size
        items.retainAll(toKeep)
        emit()
        return countBefore - items.size
    }

    override suspend fun deleteExcessSuppressed(maxSuppressed: Int): Int {
        val suppressed = items.filter { it.decision.startsWith("SUPPRESSED_") || it.decision.startsWith("IGNORED_") }
        if (suppressed.size <= maxSuppressed) return 0
        val toKeep = suppressed.sortedByDescending { it.createdAtEpochMs }.take(maxSuppressed).toSet()
        val countBefore = items.size
        items.removeAll { (it.decision.startsWith("SUPPRESSED_") || it.decision.startsWith("IGNORED_")) && it !in toKeep }
        emit()
        return countBefore - items.size
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

class HistoryRepositoryTest {

    @Test
    fun recordEventInsertsAndReturnsId() = runBlocking {
        val dao = FakeAlertEventDao()
        val repo = HistoryRepository(dao)

        val id = repo.recordEvent(
            createdAtEpochMs = 10000L,
            sourcePackage = "com.camera.app",
            notificationKey = "key-1",
            title = "Person detected",
            textPreview = "At front porch",
            normalizedHash = "hash-1",
            decision = "SCHEDULED",
            ruleId = "rule-1",
            alarmToken = "token-1",
            details = null
        )

        assertTrue(id > 0)
        assertEquals(1, repo.count())
        val first = repo.observePaged("ALL", page = 1).first()
        assertEquals(1, first.size)
        assertEquals("Person detected", first[0].title)
    }

    @Test
    fun retentionAutomaticallyPrunesOlderThan3Days() = runBlocking {
        val dao = FakeAlertEventDao()
        val repo = HistoryRepository(dao)
        val now = 10_000_000_000L
        val olderThan3Days = now - (3 * 24 * 60 * 60 * 1000L) - 1000L // 3 days and 1 second ago
        val within3Days = now - (1 * 24 * 60 * 60 * 1000L) // 1 day ago

        repo.recordEvent(
            createdAtEpochMs = olderThan3Days,
            sourcePackage = "com.camera.app",
            notificationKey = "old-1",
            title = "Old Alert",
            textPreview = null,
            normalizedHash = null,
            decision = "IGNORED_WRONG_PACKAGE",
            ruleId = null,
            alarmToken = null,
            details = null
        )

        // Now record a new event at 'now'. Prune should wipe out the event older than 3 days.
        repo.recordEvent(
            createdAtEpochMs = now,
            sourcePackage = "com.camera.app",
            notificationKey = "new-1",
            title = "New Alert",
            textPreview = null,
            normalizedHash = null,
            decision = "SCHEDULED",
            ruleId = null,
            alarmToken = null,
            details = null
        )

        assertEquals(1, repo.count())
        val remaining = repo.observePaged("ALL", page = 1).first()
        assertEquals(1, remaining.size)
        assertEquals("New Alert", remaining[0].title)
    }

    @Test
    fun retentionEnforcesMaximum100Entries() = runBlocking {
        val dao = FakeAlertEventDao()
        val repo = HistoryRepository(dao)
        val baseTime = 50_000_000L

        // Insert 120 events
        for (i in 1..120) {
            repo.recordEvent(
                createdAtEpochMs = baseTime + (i * 1000L),
                sourcePackage = "com.camera.app",
                notificationKey = "key-$i",
                title = "Alert #$i",
                textPreview = null,
                normalizedHash = null,
                decision = "SCHEDULED",
                ruleId = null,
                alarmToken = null,
                details = null
            )
        }

        // Must be capped at exactly 100
        assertEquals(100, repo.count())
        val page1 = repo.observePaged("ALL", page = 1, pageSize = 25).first()
        assertEquals(25, page1.size)
        // Newest alert (#120) must be first
        assertEquals("Alert #120", page1[0].title)
    }

    @Test
    fun paginationCorrectlyOffsetsPages() = runBlocking {
        val dao = FakeAlertEventDao()
        val repo = HistoryRepository(dao)
        val baseTime = 100_000_000L

        // Insert 60 events
        for (i in 1..60) {
            repo.recordEvent(
                createdAtEpochMs = baseTime + (i * 1000L),
                sourcePackage = "com.camera.app",
                notificationKey = "key-$i",
                title = "Alert #$i",
                textPreview = null,
                normalizedHash = null,
                decision = "SCHEDULED",
                ruleId = null,
                alarmToken = null,
                details = null
            )
        }

        assertEquals(60, repo.count())

        // Page 1: 1 - 25 (Alert #60 down to Alert #36)
        val page1 = repo.observePaged("ALL", page = 1, pageSize = 25).first()
        assertEquals(25, page1.size)
        assertEquals("Alert #60", page1[0].title)
        assertEquals("Alert #36", page1[24].title)

        // Page 2: 26 - 50 (Alert #35 down to Alert #11)
        val page2 = repo.observePaged("ALL", page = 2, pageSize = 25).first()
        assertEquals(25, page2.size)
        assertEquals("Alert #35", page2[0].title)
        assertEquals("Alert #11", page2[24].title)

        // Page 3: 51 - 60 (Alert #10 down to Alert #1)
        val page3 = repo.observePaged("ALL", page = 3, pageSize = 25).first()
        assertEquals(10, page3.size)
        assertEquals("Alert #10", page3[0].title)
        assertEquals("Alert #1", page3[9].title)
    }

    @Test
    fun suppressedEventsAreLimitedTo10Newest() = runBlocking {
        val dao = FakeAlertEventDao()
        val repo = HistoryRepository(dao)

        for (i in 1..25) {
            repo.recordEvent(
                createdAtEpochMs = 1000L * i,
                sourcePackage = "com.camera",
                notificationKey = "key-$i",
                title = "Motion $i",
                textPreview = "At front porch",
                normalizedHash = "hash-$i",
                decision = "SUPPRESSED_COOLDOWN",
                ruleId = "rule-1",
                alarmToken = null,
                details = null
            )
        }

        val suppressed = repo.observePaged("SUPPRESSED", page = 1).first()
        assertEquals(10, suppressed.size)
        assertEquals("Motion 25", suppressed.first().title)
        assertEquals("Motion 16", suppressed.last().title)
    }
}
