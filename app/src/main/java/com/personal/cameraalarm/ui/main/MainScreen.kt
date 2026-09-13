package com.personal.cameraalarm.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.permission.ReadinessRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    readinessRepo: ReadinessRepository,
    onNavigateToSourcePicker: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Camera Alarm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToRules,
                    icon = { Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = "Rules") },
                    label = { Text("Rules") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHistory,
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
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
            // Status Card
            StatusCard(state = state)

            // Ringing / Alarming Banner with STOP button
            if (state.isRinging) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🚨 ALARM ACTIVE",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.stopAlarm(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "STOP ALARM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Monitoring Toggle Card
            MonitoringCard(
                monitoringEnabled = state.monitoringEnabled,
                blockingReady = state.readiness.blockingReady,
                onToggle = { viewModel.toggleMonitoring(it) }
            )

            // Setup Checklist
            SetupChecklistCard(
                state = state,
                context = context,
                readinessRepo = readinessRepo,
                onNavigateToSourcePicker = onNavigateToSourcePicker,
                onNavigateToRules = onNavigateToRules
            )

            // Alarm Stream Volume Warning
            if (!state.readiness.alarmVolumeNonZero) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = "Volume zero warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Alarm volume is 0! Alarms will not make sound until volume is increased.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Quick Test Alarm Button
            Button(
                onClick = {
                    if (state.isRinging) viewModel.stopAlarm(context)
                    else viewModel.startTestAlarm(context)
                },
                colors = if (state.isRinging) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    if (state.isRinging) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isRinging) "STOP ALARM" else "TEST ALARM",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState) {
    val (statusText, badgeColor, textColor) = when (state.effectiveStatus) {
        AppStatus.READY -> Triple("READY", Color(0xFF2E7D32), Color.White)
        AppStatus.STANDBY -> Triple("STANDBY", Color(0xFF0288D1), Color.White)
        AppStatus.NEEDS_SETUP -> Triple("NEEDS SETUP", Color(0xFFEF6C00), Color.White)
        AppStatus.ALARMING -> Triple("ALARMING", Color(0xFFC62828), Color.White)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "System Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            state.effectiveStatus == AppStatus.STANDBY -> "Monitoring Standby"
                            state.monitoringEnabled -> "Monitoring is Active"
                            else -> "Monitoring is Off"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (state.effectiveStatus == AppStatus.STANDBY) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Outside active hours. Notifications are still monitored. Camera alarms are currently suppressed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.nextActiveTime?.let {
                        Text(
                            text = "Next active: $it",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (state.monitoringEnabled && state.effectiveStatus == AppStatus.READY) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Schedule: ${state.scheduleDescription ?: "Always active"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Sound: ${state.alarmSoundName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoringCard(
    monitoringEnabled: Boolean,
    blockingReady: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Camera Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (blockingReady) "Listen for camera notifications" else "Complete setup checklist below to enable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = monitoringEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun SetupChecklistCard(
    state: MainUiState,
    context: Context,
    readinessRepo: ReadinessRepository,
    onNavigateToSourcePicker: () -> Unit,
    onNavigateToRules: () -> Unit
) {
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
            Text(
                text = "Setup Checklist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Notification Access
            ChecklistItem(
                title = "Notification Access",
                isOk = state.readiness.notificationAccessGranted,
                statusText = if (state.readiness.notificationAccessGranted) "Granted" else "Required",
                buttonText = "Settings",
                onAction = { context.startActivity(readinessRepo.notificationAccessSettingsIntent()) }
            )

            // Exact Alarm
            ChecklistItem(
                title = "Exact Alarm Permission",
                isOk = state.readiness.exactAlarmGranted,
                statusText = if (state.readiness.exactAlarmGranted) "Granted" else "Required",
                buttonText = "Grant",
                onAction = { readinessRepo.exactAlarmSettingsIntent()?.let { context.startActivity(it) } }
            )

            // App Notifications (POST_NOTIFICATIONS)
            ChecklistItem(
                title = "App Notifications",
                isOk = state.readiness.postNotificationsGranted,
                statusText = if (state.readiness.postNotificationsGranted) "Granted" else "Required",
                buttonText = "Settings",
                onAction = { context.startActivity(readinessRepo.appNotificationSettingsIntent()) }
            )

            // Source App
            ChecklistItem(
                title = "Camera Source App",
                isOk = state.readiness.sourceConfigured,
                statusText = state.sourceLabel ?: state.sourcePackage ?: "Not selected",
                buttonText = "Select",
                onAction = onNavigateToSourcePicker
            )

            // Trigger Rules
            ChecklistItem(
                title = "Trigger Rules",
                isOk = state.readiness.ruleConfigured,
                statusText = if (state.enabledRuleCount > 0) "${state.enabledRuleCount} enabled" else "None enabled",
                buttonText = "Rules",
                onAction = onNavigateToRules
            )

            // Listener Connection
            ChecklistItem(
                title = "Listener Service",
                isOk = state.readiness.listenerConnected,
                statusText = if (state.readiness.listenerConnected) "Connected" else "Disconnected",
                buttonText = null,
                onAction = null
            )
        }
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    isOk: Boolean,
    statusText: String,
    buttonText: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOk) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (buttonText != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(text = buttonText, fontSize = 12.sp)
            }
        }
    }
}
