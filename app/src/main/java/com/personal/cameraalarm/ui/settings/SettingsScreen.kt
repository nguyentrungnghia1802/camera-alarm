package com.personal.cameraalarm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all alert history records? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Alarm Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Alarm Delay Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Alarm Delay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Time to wait between receiving camera alert and triggering alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0L to "0s", 1000L to "1s", 3000L to "3s", 5000L to "5s").forEach { (ms, label) ->
                            FilterChip(
                                selected = state.settings.alarmDelayMs == ms,
                                onClick = { viewModel.setDelay(ms) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Cooldown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Cooldown Window", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Suppresses duplicate alerts for this period after STOP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0L to "0s", 10000L to "10s", 30000L to "30s", 60000L to "60s").forEach { (ms, label) ->
                            FilterChip(
                                selected = state.settings.cooldownMs == ms,
                                onClick = { viewModel.setCooldown(ms) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Vibration & Full-screen Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibration", fontWeight = FontWeight.Bold)
                            Text("Vibrate continuously during alarm", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = state.settings.vibrationEnabled,
                            onCheckedChange = { viewModel.setVibration(it) }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full-Screen Alarm (Lock Screen)", fontWeight = FontWeight.Bold)
                            Text("Show alarm activity on lock screen (API 34+)", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = state.settings.fullScreenEnabled,
                            onCheckedChange = { viewModel.setFullScreen(it) }
                        )
                    }
                }
            }

            // Test Alarm Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Test Alarm Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Directly tests audio playback and vibration without modifying camera monitoring state.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!state.volumeStatus.isNonZero) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeMute,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Warning: Alarm stream volume is currently 0! Increase volume in system settings to hear sound.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (state.isTestingAlarm) viewModel.stopAlarm(context)
                            else viewModel.startTestAlarm(context)
                        },
                        colors = if (state.isTestingAlarm) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            if (state.isTestingAlarm) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isTestingAlarm) "STOP TEST ALARM" else "RUN TEST ALARM",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Data & Diagnostics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Data & Troubleshooting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedButton(
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Diagnostics")
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Alert History (${state.historyCount} entries)")
                    }
                }
            }
        }
    }
}
