package com.personal.cameraalarm.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.app.AppContainer
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.history.HistoryRepository
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
    val filter: HistoryFilter = HistoryFilter.ALL,
    val currentPage: Int = 1,
    val totalCount: Int = 0,
    val totalPages: Int = 1
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    private val currentPage = MutableStateFlow(1)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        selectedFilter,
        currentPage
    ) { filter, page ->
        Pair(filter, page)
    }.flatMapLatest { (filter, page) ->
        val filterName = filter.name
        combine(
            container.historyRepository.observeCount(filterName),
            container.historyRepository.observePaged(filterName, page, HistoryRepository.PAGE_SIZE)
        ) { count, pagedEvents ->
            val totalPages = maxOf(1, (count + HistoryRepository.PAGE_SIZE - 1) / HistoryRepository.PAGE_SIZE)
            val safePage = page.coerceIn(1, totalPages)
            HistoryUiState(
                events = pagedEvents,
                filter = filter,
                currentPage = safePage,
                totalCount = count,
                totalPages = totalPages
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
        currentPage.value = 1
    }

    fun nextPage() {
        val current = uiState.value
        if (current.currentPage < current.totalPages) {
            currentPage.value = current.currentPage + 1
        }
    }

    fun previousPage() {
        val current = uiState.value
        if (current.currentPage > 1) {
            currentPage.value = current.currentPage - 1
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.historyRepository.clearHistory()
            currentPage.value = 1
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
