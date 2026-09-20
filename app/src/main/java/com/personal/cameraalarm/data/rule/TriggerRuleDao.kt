package com.personal.cameraalarm.data.rule

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerRuleDao {
    @Query("SELECT * FROM trigger_rules ORDER BY priority ASC, createdAtEpochMs ASC")
    fun observeAll(): Flow<List<TriggerRuleEntity>>

    @Query("SELECT * FROM trigger_rules WHERE sourcePackage = :sourcePackage ORDER BY priority ASC, createdAtEpochMs ASC")
    fun observeForSource(sourcePackage: String): Flow<List<TriggerRuleEntity>>

    @Query("SELECT * FROM trigger_rules WHERE id = :id")
    suspend fun getById(id: String): TriggerRuleEntity?

    @Query("SELECT * FROM trigger_rules WHERE priority = :priority LIMIT 1")
    suspend fun getByPriority(priority: Int): TriggerRuleEntity?

    @Query("SELECT COUNT(*) FROM trigger_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: TriggerRuleEntity)

    @Update
    suspend fun update(rule: TriggerRuleEntity)

    @Query("UPDATE trigger_rules SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int)

    @Delete
    suspend fun delete(rule: TriggerRuleEntity)

    @Query("DELETE FROM trigger_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun saveWithPrioritySwap(rule: TriggerRuleEntity, maxNormalRules: Int = 3) {
        val existing = getById(rule.id)
        require(rule.sourcePackage.isNotBlank()) { "A source app is required." }
        require(rule.priority in 1..maxNormalRules || existing?.priority == rule.priority) {
            "Priority must be between 1 and $maxNormalRules."
        }
        if (existing == null) {
            check(count() < maxNormalRules) { "At most $maxNormalRules rules are allowed." }
            check(getByPriority(rule.priority) == null) { "Priority ${rule.priority} is already in use." }
            insert(rule)
            return
        }

        val occupant = getByPriority(rule.priority)
        if (occupant != null && occupant.id != rule.id) {
            // Free the unique target inside this transaction, then complete the swap.
            updatePriority(occupant.id, Int.MIN_VALUE)
            update(rule)
            updatePriority(occupant.id, existing.priority)
        } else {
            update(rule)
        }
    }
}
