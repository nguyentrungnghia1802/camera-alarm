package com.personal.cameraalarm.data.history

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Query("SELECT * FROM alert_events ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AlertEventEntity>>

    @Query("""
        SELECT * FROM alert_events 
        WHERE (:filter = 'ALL')
           OR (:filter = 'TRIGGERED' AND decision IN ('SCHEDULED', 'ALARM_FIRED'))
           OR (:filter = 'SUPPRESSED' AND (decision LIKE 'SUPPRESSED_%' OR decision LIKE 'IGNORED_%'))
           OR (:filter = 'ERRORS' AND decision IN ('SCHEDULE_FAILED', 'ALARM_RUNTIME_ERROR'))
        ORDER BY createdAtEpochMs DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun observePaged(filter: String, limit: Int, offset: Int): Flow<List<AlertEventEntity>>

    @Query("""
        SELECT COUNT(*) FROM alert_events 
        WHERE (:filter = 'ALL')
           OR (:filter = 'TRIGGERED' AND decision IN ('SCHEDULED', 'ALARM_FIRED'))
           OR (:filter = 'SUPPRESSED' AND (decision LIKE 'SUPPRESSED_%' OR decision LIKE 'IGNORED_%'))
           OR (:filter = 'ERRORS' AND decision IN ('SCHEDULE_FAILED', 'ALARM_RUNTIME_ERROR'))
    """)
    fun observeCount(filter: String): Flow<Int>

    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    @Query("SELECT COUNT(*) FROM alert_events")
    suspend fun count(): Int

    @Query("DELETE FROM alert_events WHERE createdAtEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("DELETE FROM alert_events WHERE id NOT IN (SELECT id FROM alert_events ORDER BY createdAtEpochMs DESC LIMIT :maxRetained)")
    suspend fun deleteExcess(maxRetained: Int): Int

    @Transaction
    suspend fun pruneRetention(cutoffEpochMs: Long, maxRetained: Int = 100) {
        deleteOlderThan(cutoffEpochMs)
        deleteExcess(maxRetained)
    }

    @Query("DELETE FROM alert_events")
    suspend fun clearAll()
}
