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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: TriggerRuleEntity)

    @Delete
    suspend fun delete(rule: TriggerRuleEntity)

    @Query("DELETE FROM trigger_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
