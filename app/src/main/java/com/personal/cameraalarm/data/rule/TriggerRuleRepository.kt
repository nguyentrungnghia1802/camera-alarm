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

    suspend fun saveRule(rule: TriggerRule) {
        dao.insertOrUpdate(TriggerRuleEntity.fromDomain(rule))
    }

    suspend fun deleteRule(id: String) {
        dao.deleteById(id)
    }
}
