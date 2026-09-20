package com.personal.cameraalarm.data.rule

import com.personal.cameraalarm.trigger.TriggerRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TriggerRuleRepository(private val dao: TriggerRuleDao) {
    val rules: Flow<List<TriggerRule>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    fun observeForSource(sourcePackage: String): Flow<List<TriggerRule>> =
        dao.observeForSource(sourcePackage).map { list -> list.map { it.toDomain() } }

    suspend fun getRule(id: String): TriggerRule? = dao.getById(id)?.toDomain()

    suspend fun saveRule(rule: TriggerRule, nowEpochMs: Long = System.currentTimeMillis()) {
        val existing = dao.getById(rule.id)
        val persisted = rule.copy(
            createdAtEpochMs = existing?.createdAtEpochMs ?: rule.createdAtEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
        dao.saveWithPrioritySwap(TriggerRuleEntity.fromDomain(persisted))
    }

    suspend fun deleteRule(id: String) {
        dao.deleteById(id)
    }
}
