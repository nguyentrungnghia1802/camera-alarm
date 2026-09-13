package com.personal.cameraalarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.ui.main.MainScreen
import com.personal.cameraalarm.ui.main.MainViewModel

class MainActivity : ComponentActivity() {
    private val app get() = application as CameraAlarmApp
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    viewModel = mainViewModel,
                    readinessRepo = app.container.readiness,
                    onNavigateToSourcePicker = { },
                    onNavigateToRules = { },
                    onNavigateToHistory = { },
                    onNavigateToSettings = { },
                    onNavigateToDiagnostics = { }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshReadiness()
    }
}
