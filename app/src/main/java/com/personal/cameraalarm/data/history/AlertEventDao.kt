package com.personal.cameraalarm.data.history

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Query("SELECT * FROM alert_events ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AlertEventEntity>>

    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    @Query("SELECT COUNT(*) FROM alert_events")
    suspend fun count(): Int

    @Query("DELETE FROM alert_events WHERE id NOT IN (SELECT id FROM alert_events ORDER BY createdAtEpochMs DESC LIMIT 500)")
    suspend fun pruneOverRetention()

    @Query("DELETE FROM alert_events")
    suspend fun clearAll()
}
