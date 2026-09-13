package com.personal.cameraalarm.schedule

object ScheduleSerializer {
    /**
     * Serializes ranges as "id|start|end|enabled" separated by ";".
     * Pure Kotlin implementation with zero platform dependencies.
     */
    fun serialize(ranges: List<ActiveTimeRange>): String {
        return ranges.joinToString(";") { "${it.id}|${it.startMinutes}|${it.endMinutes}|${it.enabled}" }
    }

    fun deserialize(serialized: String?): List<ActiveTimeRange> {
        if (serialized.isNullOrBlank()) return emptyList()
        return serialized.split(";").mapNotNull { token ->
            val parts = token.split("|")
            if (parts.size == 4) {
                val start = parts[1].toIntOrNull() ?: return@mapNotNull null
                val end = parts[2].toIntOrNull() ?: return@mapNotNull null
                val enabled = parts[3].toBooleanStrictOrNull() ?: true
                ActiveTimeRange(
                    id = parts[0],
                    startMinutes = start,
                    endMinutes = end,
                    enabled = enabled
                )
            } else null
        }
    }
}
