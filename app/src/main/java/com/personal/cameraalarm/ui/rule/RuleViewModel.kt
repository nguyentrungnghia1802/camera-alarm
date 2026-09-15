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

data class RuleEditorState(
    val id: String? = null,
    val name: String = "",
    val sourcePackage: String = "",
    val matchMode: MatchMode = MatchMode.CONTAINS_ANY,
    val keywordsRaw: String = "",
    val priority: Int = 1,
    val enabled: Boolean = true,
    val errorMessage: String? = null
) {
    val normalizedKeywords: List<String>
        get() {
            return keywordsRaw.split(Regex("[\n,]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { NotificationNormalizer.normalize(it) }
                .filter { it.isNotEmpty() }
                .distinct()
        }
}

class RuleViewModel(private val container: AppContainer) : ViewModel() {
    val rules: StateFlow<List<TriggerRule>> = container.ruleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editorState = MutableStateFlow(RuleEditorState())
    val editorState: StateFlow<RuleEditorState> = _editorState.asStateFlow()

    fun initNewRule() {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            _editorState.value = RuleEditorState(
                id = UUID.randomUUID().toString(),
                name = "",
                sourcePackage = settings.sourcePackage ?: "",
                matchMode = MatchMode.CONTAINS_ANY,
                keywordsRaw = "",
                priority = 1,
                enabled = true,
                errorMessage = null
            )
        }
    }

    fun initFromTemplate() {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            _editorState.value = RuleEditorState(
                id = UUID.randomUUID().toString(),
                name = "Person / motion alert",
                sourcePackage = settings.sourcePackage ?: "",
                matchMode = MatchMode.CONTAINS_ANY,
                keywordsRaw = listOf(
                    "human detected",
                    "person detected",
                    "motion detected",
                    "phát hiện người",
                    "phát hiện chuyển động"
                ).joinToString("\n"),
                priority = 1,
                enabled = false, // Not enabled by default until user confirms
                errorMessage = null
            )
        }
    }

    fun initEditRule(rule: TriggerRule) {
        _editorState.value = RuleEditorState(
            id = rule.id,
            name = rule.name,
            sourcePackage = rule.sourcePackage,
            matchMode = rule.matchMode,
            keywordsRaw = rule.keywords.joinToString("\n"),
            priority = rule.priority,
            enabled = rule.enabled,
            errorMessage = null
        )
    }

    fun updateName(name: String) {
        _editorState.value = _editorState.value.copy(name = name, errorMessage = null)
    }

    fun updateMatchMode(mode: MatchMode) {
        _editorState.value = _editorState.value.copy(matchMode = mode)
    }

    fun updateKeywordsRaw(raw: String) {
        _editorState.value = _editorState.value.copy(keywordsRaw = raw, errorMessage = null)
    }

    fun updatePriority(priority: Int) {
        _editorState.value = _editorState.value.copy(priority = priority.coerceIn(1, 100))
    }

    fun updateEnabled(enabled: Boolean) {
        _editorState.value = _editorState.value.copy(enabled = enabled)
    }

    fun saveRule(onSuccess: () -> Unit) {
        val s = _editorState.value
        val validation = validateRule(s)
        if (validation != null) {
            _editorState.value = s.copy(errorMessage = validation)
            return
        }

        val rule = TriggerRule(
            id = s.id ?: UUID.randomUUID().toString(),
            name = s.name.trim(),
            enabled = s.enabled,
            sourcePackage = s.sourcePackage.trim(),
            matchMode = s.matchMode,
            keywords = s.normalizedKeywords,
            priority = s.priority,
            createdAtEpochMs = System.currentTimeMillis()
        )

        viewModelScope.launch {
            container.ruleRepository.saveRule(rule)
            onSuccess()
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
        fun validateRule(state: RuleEditorState): String? {
            if (state.name.isBlank()) return "Rule name cannot be blank."
            if (state.sourcePackage.isBlank()) return "Source package cannot be blank. Select a source app first."
            val rawLines = state.keywordsRaw.split(Regex("[\n,]")).map { it.trim() }.filter { it.isNotEmpty() }
            if (rawLines.isEmpty()) return "At least one valid keyword is required."
            if (rawLines.any { it.length > 100 }) return "Each keyword must be 100 characters or fewer."
            val normalized = state.normalizedKeywords
            if (normalized.isEmpty()) return "At least one valid keyword is required."
            if (normalized.size > 30) return "A maximum of 30 keywords is allowed."
            val shortKw = normalized.firstOrNull { it.length < 2 }
            if (shortKw != null) {
                return "Keyword \"$shortKw\" is too short (under 2 characters) and may cause false alarms."
            }
            return null
        }

        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RuleViewModel(container) as T
            }
    }
}
