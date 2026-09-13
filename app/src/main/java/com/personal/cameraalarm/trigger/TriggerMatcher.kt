package com.personal.cameraalarm.trigger

object TriggerMatcher {
    fun match(sourcePackage: String, searchableText: String, rules: List<TriggerRule>): TriggerRule? =
        rules.asSequence()
            .filter { it.enabled && it.sourcePackage == sourcePackage }
            .sortedWith(compareBy<TriggerRule> { it.priority }.thenBy { it.createdAtEpochMs }.thenBy { it.id })
            .firstOrNull { rule ->
                val words = rule.keywords.map(NotificationNormalizer::normalize).filter(String::isNotEmpty).distinct()
                words.isNotEmpty() && when (rule.matchMode) {
                    MatchMode.CONTAINS_ANY -> words.any(searchableText::contains)
                    MatchMode.CONTAINS_ALL -> words.all(searchableText::contains)
                }
            }
}
