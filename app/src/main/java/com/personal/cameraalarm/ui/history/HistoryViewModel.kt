package com.personal.cameraalarm.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.data.history.AlertEventEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    TRIGGERED,
    SUPPRESSED,
    ERRORS
}

data class HistoryUiState(
    val events: List<AlertEventEntity> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedFilter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        container.historyRepository.events,
        selectedFilter
    ) { allEvents, filter ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> allEvents
            HistoryFilter.TRIGGERED -> allEvents.filter {
                it.decision == "SCHEDULED" || it.decision == "ALARM_FIRED"
            }
            HistoryFilter.SUPPRESSED -> allEvents.filter {
                it.decision.startsWith("SUPPRESSED_") || it.decision.startsWith("IGNORED_")
            }
            HistoryFilter.ERRORS -> allEvents.filter {
                it.decision == "SCHEDULE_FAILED" || it.decision == "ALARM_RUNTIME_ERROR"
            }
        }
        HistoryUiState(events = filtered, filter = filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.historyRepository.clearHistory()
        }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HistoryViewModel(container) as T
            }
    }
}
