package com.personal.cameraalarm.data.rule

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerRule

@Entity(tableName = "trigger_rules")
data class TriggerRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val sourcePackage: String,
    val matchMode: String,
    val keywordsJson: String,
    val priority: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toDomain(): TriggerRule {
        val keywordsList = decodeKeywords(keywordsJson)
        val mode = try {
            MatchMode.valueOf(matchMode)
        } catch (_: Exception) {
            MatchMode.CONTAINS_ANY
        }
        return TriggerRule(
            id = id,
            name = name,
            enabled = enabled,
            sourcePackage = sourcePackage,
            matchMode = mode,
            keywords = keywordsList,
            priority = priority,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    companion object {
        fun fromDomain(rule: TriggerRule, updatedAtEpochMs: Long = System.currentTimeMillis()): TriggerRuleEntity {
            return TriggerRuleEntity(
                id = rule.id,
                name = rule.name,
                enabled = rule.enabled,
                sourcePackage = rule.sourcePackage,
                matchMode = rule.matchMode.name,
                keywordsJson = encodeKeywords(rule.keywords),
                priority = rule.priority,
                createdAtEpochMs = rule.createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs
            )
        }

        fun encodeKeywords(items: List<String>): String {
            return items.joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
                "\"" + item.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
            }
        }

        fun decodeKeywords(json: String): List<String> {
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isEmpty()) return emptyList()
            val result = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var escaped = false
            for (char in inner) {
                when {
                    escaped -> {
                        when (char) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            '\\' -> sb.append('\\')
                            '"' -> sb.append('"')
                            else -> sb.append(char)
                        }
                        escaped = false
                    }
                    char == '\\' && inQuotes -> escaped = true
                    char == '"' -> inQuotes = !inQuotes
                    char == ',' && !inQuotes -> {
                        result.add(sb.toString())
                        sb.clear()
                    }
                    else -> if (inQuotes) sb.append(char)
                }
            }
            if (sb.isNotEmpty() || inner.endsWith("\"")) {
                result.add(sb.toString())
            }
            return result
        }
    }
}
