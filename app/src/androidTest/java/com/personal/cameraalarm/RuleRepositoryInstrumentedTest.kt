package com.personal.cameraalarm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.cameraalarm.data.AppDatabase
import com.personal.cameraalarm.data.rule.TriggerRuleRepository
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleRepositoryInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TriggerRuleRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        repository = TriggerRuleRepository(database.triggerRuleDao())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun maximumThreeRulesAndUniquePrioritiesAreEnforced() = runBlocking {
        repository.saveRule(rule("one", 1), nowEpochMs = 101)
        repository.saveRule(rule("two", 2), nowEpochMs = 102)
        repository.saveRule(rule("three", 3), nowEpochMs = 103)

        val fourth = runCatching { repository.saveRule(rule("four", 1), nowEpochMs = 104) }
        assertTrue(fourth.isFailure)
        assertEquals(listOf(1, 2, 3), repository.rules.first().map { it.priority })
    }

    @Test
    fun editingToOccupiedPrioritySwapsAtomicallyAndPreservesCreatedAt() = runBlocking {
        repository.saveRule(rule("one", 1, createdAt = 10), nowEpochMs = 100)
        repository.saveRule(rule("two", 2, createdAt = 20), nowEpochMs = 200)

        val original = repository.getRule("one")!!
        repository.saveRule(
            original.copy(name = "Edited", priority = 2, createdAtEpochMs = 999),
            nowEpochMs = 300
        )

        val one = repository.getRule("one")!!
        val two = repository.getRule("two")!!
        assertEquals(2, one.priority)
        assertEquals(1, two.priority)
        assertEquals(10, one.createdAtEpochMs)
        assertEquals(300, one.updatedAtEpochMs)
    }

    @Test
    fun deleteDoesNotRenumberRemainingRules() = runBlocking {
        repository.saveRule(rule("one", 1), nowEpochMs = 101)
        repository.saveRule(rule("two", 2), nowEpochMs = 102)
        repository.saveRule(rule("three", 3), nowEpochMs = 103)

        repository.deleteRule("two")

        assertEquals(listOf(1, 3), repository.rules.first().map { it.priority })
    }

    private fun rule(id: String, priority: Int, createdAt: Long = priority.toLong()) = TriggerRule(
        id = id,
        name = id,
        enabled = true,
        sourcePackage = "camera.$id",
        matchMode = MatchMode.CONTAINS_ANY,
        keywords = listOf("person"),
        priority = priority,
        createdAtEpochMs = createdAt
    )
}
