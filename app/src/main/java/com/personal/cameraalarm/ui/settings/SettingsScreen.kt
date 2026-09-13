package com.personal.cameraalarm.ui.settings

import androidx.compose.foundation.clickable
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
    var showAddRangeDialog by remember { mutableStateOf(false) }
    var editingRange by remember { mutableStateOf<com.personal.cameraalarm.schedule.ActiveTimeRange?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreview()
        }
    }

    if (showAddRangeDialog) {
        TimeRangeEditDialog(
            initialRange = null,
            onDismiss = { showAddRangeDialog = false },
            onSave = { start, end ->
                viewModel.addScheduleRange(start, end)
                showAddRangeDialog = false
            }
        )
    }

    editingRange?.let { range ->
        TimeRangeEditDialog(
            initialRange = range,
            onDismiss = { editingRange = null },
            onSave = { start, end ->
                viewModel.updateScheduleRange(range.id, start, end, range.enabled)
                editingRange = null
            }
        )
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
            // Alarm Sound Card
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
                    Text("Alarm Sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Select bundled MP3 sound for camera alert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    viewModel.availableSounds.forEach { sound ->
                        val isSelected = state.settings.alarmSoundKey == sound.key
                        val isPreviewingThis = state.previewPlayingKey == sound.key

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectAlarmSound(sound.key) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectAlarmSound(sound.key) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sound.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (isPreviewingThis) viewModel.stopPreview()
                                    else viewModel.playPreview(sound)
                                }
                            ) {
                                Icon(
                                    if (isPreviewingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPreviewingThis) "Stop Preview" else "Play Preview",
                                    tint = if (isPreviewingThis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Alarm Schedule Card
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
                    Text("Alarm Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Define active hours when camera alerts are allowed to sound alarms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.settings.scheduleMode == com.personal.cameraalarm.schedule.ScheduleMode.ALWAYS_ACTIVE,
                            onClick = { viewModel.setScheduleMode(com.personal.cameraalarm.schedule.ScheduleMode.ALWAYS_ACTIVE) },
                            label = { Text("Always active") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.settings.scheduleMode == com.personal.cameraalarm.schedule.ScheduleMode.CUSTOM,
                            onClick = { viewModel.setScheduleMode(com.personal.cameraalarm.schedule.ScheduleMode.CUSTOM) },
                            label = { Text("Custom active hours") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state.settings.scheduleMode == com.personal.cameraalarm.schedule.ScheduleMode.CUSTOM) {
                        HorizontalDivider()

                        val ranges = state.settings.scheduleRanges
                        val enabledCount = ranges.count { it.enabled }

                        if (ranges.isEmpty() || enabledCount == 0) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "No active time ranges. Camera alerts will not ring.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        ranges.forEach { range ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${range.formatStart()} → ${range.formatEnd()}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (range.enabled) FontWeight.Bold else FontWeight.Normal,
                                        color = if (range.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (range.startMinutes > range.endMinutes) {
                                        Text(
                                            text = "Overnight span",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (range.startMinutes == range.endMinutes) {
                                        Text(
                                            text = "24-hour full day",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = range.enabled,
                                        onCheckedChange = { viewModel.toggleScheduleRange(range.id, it) }
                                    )
                                    IconButton(onClick = { editingRange = range }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit range")
                                    }
                                    IconButton(onClick = { viewModel.deleteScheduleRange(range.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete range", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddRangeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+ Add time range")
                        }
                    }
                }
            }

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

@Composable
private fun TimeRangeEditDialog(
    initialRange: com.personal.cameraalarm.schedule.ActiveTimeRange?,
    onDismiss: () -> Unit,
    onSave: (startMinutes: Int, endMinutes: Int) -> Unit
) {
    var startHour by remember { mutableIntStateOf(initialRange?.let { it.startMinutes / 60 } ?: 23) }
    var startMinute by remember { mutableIntStateOf(initialRange?.let { it.startMinutes % 60 } ?: 0) }
    var endHour by remember { mutableIntStateOf(initialRange?.let { it.endMinutes / 60 } ?: 7) }
    var endMinute by remember { mutableIntStateOf(initialRange?.let { it.endMinutes % 60 } ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRange != null) "Edit Time Range" else "Add Time Range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Configure start and end clock times in current device timezone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Start Time
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Start Time (Inclusive):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = "%02d".format(startHour),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { if (it in 0..23) startHour = it }
                            },
                            label = { Text("Hour (0-23)") },
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = "%02d".format(startMinute),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { if (it in 0..59) startMinute = it }
                            },
                            label = { Text("Min (0-59)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // End Time
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("End Time (Exclusive):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = "%02d".format(endHour),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { if (it in 0..23) endHour = it }
                            },
                            label = { Text("Hour (0-23)") },
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = "%02d".format(endMinute),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { if (it in 0..59) endMinute = it }
                            },
                            label = { Text("Min (0-59)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (startHour * 60 + startMinute > endHour * 60 + endMinute) {
                    Text(
                        text = "ℹ Overnight range: active from %02d:%02d through midnight until %02d:%02d".format(startHour, startMinute, endHour, endMinute),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (startHour * 60 + startMinute == endHour * 60 + endMinute) {
                    Text(
                        text = "ℹ Equal start & end: active full 24 hours continuously",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val startM = startHour * 60 + startMinute
                val endM = endHour * 60 + endMinute
                onSave(startM, endM)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
