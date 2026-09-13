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
        dao.pruneOverRetention()
        return id
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun count(): Int = dao.count()
}
