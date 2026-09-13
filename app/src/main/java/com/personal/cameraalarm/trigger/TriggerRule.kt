package com.personal.cameraalarm.trigger

enum class MatchMode { CONTAINS_ANY, CONTAINS_ALL }

data class TriggerRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val sourcePackage: String,
    val matchMode: MatchMode,
    val keywords: List<String>,
    val priority: Int,
    val createdAtEpochMs: Long
)
