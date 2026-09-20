package com.personal.cameraalarm.data.history

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: AlertEventDao) {
    val events: Flow<List<AlertEventEntity>> = dao.observeAll()

    suspend fun recordEvent(
        createdAtEpochMs: Long,
        sourcePackage: String?,
        notificationKey: String?,
        title: String?,
        textPreview: String?,
        normalizedHash: String?,
        decision: String,
        ruleId: String?,
        alarmToken: String?,
        details: String?
    ): Long {
        val entity = AlertEventEntity(
            createdAtEpochMs = createdAtEpochMs,
            sourcePackage = sourcePackage,
            notificationKey = notificationKey,
            title = title?.take(300),
            textPreview = textPreview?.take(300),
            normalizedHash = normalizedHash,
            decision = decision,
            ruleId = ruleId,
            alarmToken = alarmToken,
            details = details?.take(500)
        )
        val id = dao.insert(entity)
        val cutoff = createdAtEpochMs - RETENTION_WINDOW_MS
        dao.pruneRetention(cutoff, MAX_RETAINED, MAX_SUPPRESSED_RETAINED)
        return id
    }

    suspend fun prune(nowEpochMs: Long = System.currentTimeMillis()) {
        val cutoff = nowEpochMs - RETENTION_WINDOW_MS
        dao.pruneRetention(cutoff, MAX_RETAINED, MAX_SUPPRESSED_RETAINED)
    }

    fun observePaged(filter: String, page: Int, pageSize: Int = PAGE_SIZE): Flow<List<AlertEventEntity>> {
        val safePage = page.coerceAtLeast(1)
        val offset = (safePage - 1) * pageSize
        return dao.observePaged(filter, pageSize, offset)
    }

    fun observeCount(filter: String): Flow<Int> = dao.observeCount(filter)

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun deleteLegacyUnrelatedNotificationRows(): Int =
        dao.deleteByDecisions(listOf("IGNORED_WRONG_PACKAGE", "IGNORED_MONITORING_OFF"))

    suspend fun count(): Int = dao.count()

    companion object {
        const val PAGE_SIZE = 25
        const val MAX_RETAINED = 100
        const val MAX_SUPPRESSED_RETAINED = 10
        const val RETENTION_WINDOW_MS = 3 * 24 * 60 * 60 * 1000L // 3 days
    }
}
