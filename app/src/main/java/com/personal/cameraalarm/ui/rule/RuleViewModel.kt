package com.personal.cameraalarm.ui.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.NotificationNormalizer
import com.personal.cameraalarm.trigger.TriggerRule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface RuleEditorError {
    data object MaxRules : RuleEditorError
    data object KeywordBlank : RuleEditorError
    data object KeywordTooLong : RuleEditorError
    data object KeywordDuplicate : RuleEditorError
    data object NameBlank : RuleEditorError
    data object SourceBlank : RuleEditorError
    data object KeywordsRequired : RuleEditorError
    data object TooManyKeywords : RuleEditorError
    data class KeywordTooShort(val keyword: String) : RuleEditorError
    data object SaveFailed : RuleEditorError
}

data class RuleEditorState(
    val id: String? = null,
    val isNew: Boolean = true,
    val name: String = "",
    val sourcePackage: String = "",
    val sourceLabel: String = "",
    val matchMode: MatchMode = MatchMode.CONTAINS_ANY,
    val keywordsRaw: String = "",
    val priority: Int = 1,
    val enabled: Boolean = true,
    val createdAtEpochMs: Long = 0L,
    val error: RuleEditorError? = null
) {
    val keywordItems: List<String>
        get() = keywordsRaw.lines().map(String::trim).filter(String::isNotEmpty)

    val normalizedKeywords: List<String>
        get() = keywordItems.map(NotificationNormalizer::normalize).filter(String::isNotEmpty).distinct()
}

class RuleViewModel(private val container: AppContainer) : ViewModel() {
    val rules: StateFlow<List<TriggerRule>> = container.ruleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editorState = MutableStateFlow(RuleEditorState())
    val editorState: StateFlow<RuleEditorState> = _editorState.asStateFlow()

    fun initNewRule() {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val currentRules = rules.value
            _editorState.value = RuleEditorState(
                id = UUID.randomUUID().toString(),
                isNew = true,
                name = "",
                sourcePackage = settings.sourcePackage ?: "",
                sourceLabel = settings.sourceLabel ?: settings.sourcePackage.orEmpty(),
                matchMode = MatchMode.CONTAINS_ANY,
                keywordsRaw = "",
                priority = firstAvailablePriority(currentRules),
                enabled = true,
                error = if (currentRules.size >= MAX_RULES) RuleEditorError.MaxRules else null
            )
        }
    }

    fun initFromTemplate() {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            _editorState.value = RuleEditorState(
                id = UUID.randomUUID().toString(),
                isNew = true,
                name = SUGGESTED_TEMPLATE_NAME,
                sourcePackage = settings.sourcePackage ?: "",
                sourceLabel = settings.sourceLabel ?: settings.sourcePackage.orEmpty(),
                matchMode = MatchMode.CONTAINS_ANY,
                keywordsRaw = SUGGESTED_TEMPLATE_KEYWORDS.joinToString("\n"),
                priority = firstAvailablePriority(rules.value),
                enabled = true,
                error = if (rules.value.size >= MAX_RULES) RuleEditorError.MaxRules else null
            )
        }
    }

    fun initEditRule(rule: TriggerRule) {
        _editorState.value = RuleEditorState(
            id = rule.id,
            isNew = false,
            name = rule.name,
            sourcePackage = rule.sourcePackage,
            sourceLabel = rule.sourcePackage,
            matchMode = rule.matchMode,
            keywordsRaw = rule.keywords.joinToString("\n"),
            priority = rule.priority,
            enabled = rule.enabled,
            createdAtEpochMs = rule.createdAtEpochMs,
            error = null
        )
    }

    fun updateName(name: String) {
        _editorState.value = _editorState.value.copy(name = name, error = null)
    }

    fun updateMatchMode(mode: MatchMode) {
        _editorState.value = _editorState.value.copy(matchMode = mode)
    }

    fun updateKeywordsRaw(raw: String) {
        _editorState.value = _editorState.value.copy(keywordsRaw = raw, error = null)
    }

    fun addKeyword(value: String): Boolean = updateKeywordAt(null, value)

    fun editKeyword(index: Int, value: String): Boolean = updateKeywordAt(index, value)

    fun deleteKeyword(index: Int) {
        val items = _editorState.value.keywordItems.toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            _editorState.value = _editorState.value.copy(
                keywordsRaw = items.joinToString("\n"),
                error = null
            )
        }
    }

    private fun updateKeywordAt(index: Int?, value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _editorState.value = _editorState.value.copy(error = RuleEditorError.KeywordBlank)
            return false
        }
        if (trimmed.length > 100) {
            _editorState.value = _editorState.value.copy(error = RuleEditorError.KeywordTooLong)
            return false
        }
        val normalized = NotificationNormalizer.normalize(trimmed)
        val items = _editorState.value.keywordItems.toMutableList()
        val duplicate = items.indices.any { other ->
            other != index && NotificationNormalizer.normalize(items[other]) == normalized
        }
        if (duplicate) {
            _editorState.value = _editorState.value.copy(error = RuleEditorError.KeywordDuplicate)
            return false
        }
        if (index == null) items += trimmed else if (index in items.indices) items[index] = trimmed else return false
        _editorState.value = _editorState.value.copy(
            keywordsRaw = items.joinToString("\n"),
            error = null
        )
        return true
    }

    fun updateSourceApp(packageName: String, label: String) {
        _editorState.value = _editorState.value.copy(
            sourcePackage = packageName.trim(),
            sourceLabel = label.trim(),
            error = null
        )
    }

    fun updatePriority(priority: Int) {
        _editorState.value = _editorState.value.copy(priority = priority.coerceIn(1, MAX_RULES))
    }

    fun updateEnabled(enabled: Boolean) {
        _editorState.value = _editorState.value.copy(enabled = enabled)
    }

    fun saveRule(onSuccess: () -> Unit) {
        val s = _editorState.value
        val validation = validateRule(s)
        if (validation != null) {
            _editorState.value = s.copy(error = validation)
            return
        }

        val rule = TriggerRule(
            id = s.id ?: UUID.randomUUID().toString(),
            name = s.name.trim(),
            enabled = s.enabled,
            sourcePackage = s.sourcePackage.trim(),
            matchMode = s.matchMode,
            keywords = s.keywordItems.distinctBy(NotificationNormalizer::normalize),
            priority = s.priority,
            createdAtEpochMs = s.createdAtEpochMs.takeIf { !s.isNew && it > 0 } ?: System.currentTimeMillis()
        )

        viewModelScope.launch {
            runCatching { container.ruleRepository.saveRule(rule) }
                .onSuccess { onSuccess() }
                .onFailure { error ->
                    container.runtimeDiagnostics.record(
                        "rule save: ${error.message ?: error.javaClass.simpleName}"
                    )
                    _editorState.value = _editorState.value.copy(
                        error = RuleEditorError.SaveFailed
                    )
                }
        }
    }

    fun toggleRuleEnabled(rule: TriggerRule) {
        viewModelScope.launch {
            container.ruleRepository.saveRule(rule.copy(enabled = !rule.enabled))
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            container.ruleRepository.deleteRule(id)
        }
    }

    companion object {
        fun validateRule(state: RuleEditorState): RuleEditorError? {
            if (state.name.isBlank()) return RuleEditorError.NameBlank
            if (state.sourcePackage.isBlank()) return RuleEditorError.SourceBlank
            val rawLines = state.keywordItems
            if (rawLines.isEmpty()) return RuleEditorError.KeywordsRequired
            if (rawLines.any { it.length > 100 }) return RuleEditorError.KeywordTooLong
            val normalized = state.normalizedKeywords
            if (normalized.isEmpty()) return RuleEditorError.KeywordsRequired
            if (normalized.size != rawLines.size) return RuleEditorError.KeywordDuplicate
            if (normalized.size > 30) return RuleEditorError.TooManyKeywords
            val shortKw = normalized.firstOrNull { it.length < 2 }
            if (shortKw != null) {
                return RuleEditorError.KeywordTooShort(shortKw)
            }
            return null
        }

        const val MAX_RULES = 3
        const val SUGGESTED_TEMPLATE_NAME = "Camera an ninh phổ biến"
        val SUGGESTED_TEMPLATE_KEYWORDS = listOf(
            "phát hiện người",
            "đã phát hiện người",
            "phát hiện con người",
            "phát hiện chuyển động",
            "phát hiện chuyển động người",
            "phát hiện phương tiện",
            "phát hiện người/phương tiện",
            "phát hiện vượt ranh giới",
            "phát hiện xâm nhập"
        )

        fun firstAvailablePriority(rules: List<TriggerRule>): Int =
            (1..MAX_RULES).firstOrNull { candidate -> rules.none { it.priority == candidate } } ?: 1

        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RuleViewModel(container) as T
            }
    }
}
