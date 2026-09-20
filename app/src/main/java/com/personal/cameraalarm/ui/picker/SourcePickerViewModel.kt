package com.personal.cameraalarm.ui.picker

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.cameraalarm.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable? = null
)

data class SourcePickerUiState(
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val selectedPackage: String? = null,
    val manualInput: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class SourcePickerViewModel(
    private val container: AppContainer,
    private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(SourcePickerUiState())
    val uiState: StateFlow<SourcePickerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps(selectedPackageOverride: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val currentSettings = container.settingsRepository.settings.first()
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                resolveInfos.map { info ->
                    val pkg = info.activityInfo.packageName
                    val label = info.loadLabel(pm).toString().takeIf(String::isNotBlank) ?: pkg
                    AppInfo(pkg, label, info.loadIcon(pm))
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }
            _uiState.value = _uiState.value.copy(
                allApps = apps,
                filteredApps = filterApps(apps, _uiState.value.searchQuery),
                selectedPackage = selectedPackageOverride ?: currentSettings.sourcePackage,
                manualInput = selectedPackageOverride ?: currentSettings.sourcePackage ?: "",
                isLoading = false
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filterApps(_uiState.value.allApps, query)
        )
    }

    fun onManualInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(manualInput = input, errorMessage = null)
    }

    fun selectApp(app: AppInfo, persistGlobal: Boolean = true, onComplete: (AppInfo) -> Unit) {
        viewModelScope.launch {
            if (persistGlobal) container.settingsRepository.setSourceApp(app.packageName, app.label)
            _uiState.value = _uiState.value.copy(selectedPackage = app.packageName)
            onComplete(app)
        }
    }

    fun submitManualInput(persistGlobal: Boolean = true, onComplete: (AppInfo) -> Unit) {
        val trimmed = _uiState.value.manualInput.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Package name cannot be blank")
            return
        }
        viewModelScope.launch {
            val matchingApp = _uiState.value.allApps.firstOrNull { it.packageName == trimmed }
            val label = matchingApp?.label ?: trimmed
            val app = matchingApp ?: AppInfo(trimmed, label)
            if (persistGlobal) container.settingsRepository.setSourceApp(trimmed, label)
            _uiState.value = _uiState.value.copy(selectedPackage = trimmed)
            onComplete(app)
        }
    }

    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        val normalized = query.lowercase().trim()
        return apps.filter {
            it.label.lowercase().contains(normalized) || it.packageName.lowercase().contains(normalized)
        }
    }

    companion object {
        fun provideFactory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SourcePickerViewModel(container, context) as T
            }
    }
}
