package com.personal.cameraalarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.ui.diagnostics.DiagnosticsScreen
import com.personal.cameraalarm.ui.diagnostics.DiagnosticsViewModel
import com.personal.cameraalarm.ui.history.HistoryScreen
import com.personal.cameraalarm.ui.history.HistoryViewModel
import com.personal.cameraalarm.ui.main.MainScreen
import com.personal.cameraalarm.ui.main.MainViewModel
import com.personal.cameraalarm.ui.picker.SourcePickerScreen
import com.personal.cameraalarm.ui.picker.SourcePickerViewModel
import com.personal.cameraalarm.ui.rule.RuleEditorScreen
import com.personal.cameraalarm.ui.rule.RuleListScreen
import com.personal.cameraalarm.ui.rule.RuleViewModel
import com.personal.cameraalarm.ui.settings.SettingsScreen
import com.personal.cameraalarm.ui.settings.SettingsViewModel
import com.personal.cameraalarm.ui.settings.SoundPickerScreen
import com.personal.cameraalarm.ui.theme.CameraAlarmTheme

enum class AppScreen {
    DASHBOARD,
    SOURCE_PICKER,
    RULE_SOURCE_PICKER,
    RULES,
    RULE_EDITOR,
    SETTINGS,
    SOUND_PICKER,
    HISTORY,
    DIAGNOSTICS
}

class MainActivity : ComponentActivity() {
    private val app get() = application as CameraAlarmApp

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(app.container)
    }
    private val sourcePickerViewModel: SourcePickerViewModel by viewModels {
        SourcePickerViewModel.provideFactory(app.container, applicationContext)
    }
    private val ruleViewModel: RuleViewModel by viewModels {
        RuleViewModel.provideFactory(app.container)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.provideFactory(app.container)
    }
    private val historyViewModel: HistoryViewModel by viewModels {
        HistoryViewModel.provideFactory(app.container)
    }
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels {
        DiagnosticsViewModel.provideFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by app.container.settingsRepository.settings.collectAsState(initial = com.personal.cameraalarm.data.settings.AppSettings())
            val currentLang = settings.language
            val locale = remember(currentLang) { java.util.Locale(currentLang) }
            val configuration = remember(locale) {
                val conf = android.content.res.Configuration(resources.configuration)
                conf.setLocale(locale)
                conf
            }
            val localizedContext = remember(locale) {
                createConfigurationContext(configuration)
            }

            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
                androidx.compose.ui.platform.LocalContext provides localizedContext
            ) {
                CameraAlarmTheme {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }

                if (currentScreen != AppScreen.DASHBOARD) {
                    BackHandler {
                        currentScreen = when (currentScreen) {
                            AppScreen.RULE_EDITOR -> AppScreen.RULES
                            AppScreen.RULE_SOURCE_PICKER -> AppScreen.RULE_EDITOR
                            AppScreen.SOUND_PICKER -> AppScreen.SETTINGS
                            AppScreen.DIAGNOSTICS -> AppScreen.SETTINGS
                            else -> AppScreen.DASHBOARD
                        }
                    }
                }

                when (currentScreen) {
                    AppScreen.DASHBOARD -> MainScreen(
                        viewModel = mainViewModel,
                        readinessRepo = app.container.readiness,
                        onNavigateToSourcePicker = {
                            sourcePickerViewModel.loadApps()
                            currentScreen = AppScreen.SOURCE_PICKER
                        },
                        onNavigateToRules = { currentScreen = AppScreen.RULES },
                        onNavigateToHistory = { currentScreen = AppScreen.HISTORY },
                        onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                        onNavigateToSoundPicker = { currentScreen = AppScreen.SOUND_PICKER }
                    )
                    AppScreen.SOURCE_PICKER -> SourcePickerScreen(
                        viewModel = sourcePickerViewModel,
                        onBack = { currentScreen = AppScreen.DASHBOARD }
                    )
                    AppScreen.RULE_SOURCE_PICKER -> SourcePickerScreen(
                        viewModel = sourcePickerViewModel,
                        onBack = { currentScreen = AppScreen.RULE_EDITOR },
                        onAppSelected = { appInfo ->
                            ruleViewModel.updateSourceApp(appInfo.packageName, appInfo.label)
                        }
                    )
                    AppScreen.RULES -> RuleListScreen(
                        viewModel = ruleViewModel,
                        onBack = { currentScreen = AppScreen.DASHBOARD },
                        onNavigate = { currentScreen = it },
                        onAddRule = { currentScreen = AppScreen.RULE_EDITOR },
                        onEditRule = { currentScreen = AppScreen.RULE_EDITOR }
                    )
                    AppScreen.RULE_EDITOR -> RuleEditorScreen(
                        viewModel = ruleViewModel,
                        onBack = { currentScreen = AppScreen.RULES },
                        onSelectSourceApp = {
                            sourcePickerViewModel.loadApps(ruleViewModel.editorState.value.sourcePackage)
                            currentScreen = AppScreen.RULE_SOURCE_PICKER
                        }
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { currentScreen = AppScreen.DASHBOARD },
                        onNavigate = { currentScreen = it },
                        onNavigateToSoundPicker = { currentScreen = AppScreen.SOUND_PICKER },
                        onNavigateToDiagnostics = { currentScreen = AppScreen.DIAGNOSTICS }
                    )
                    AppScreen.SOUND_PICKER -> SoundPickerScreen(
                        viewModel = settingsViewModel,
                        onBack = { currentScreen = AppScreen.SETTINGS }
                    )
                    AppScreen.HISTORY -> HistoryScreen(
                        viewModel = historyViewModel,
                        onBack = { currentScreen = AppScreen.DASHBOARD },
                        onNavigate = { currentScreen = it }
                    )
                    AppScreen.DIAGNOSTICS -> DiagnosticsScreen(
                        viewModel = diagnosticsViewModel,
                        readinessRepo = app.container.readiness,
                        onBack = { currentScreen = AppScreen.SETTINGS }
                    )
                }
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshReadiness()
    }
}
