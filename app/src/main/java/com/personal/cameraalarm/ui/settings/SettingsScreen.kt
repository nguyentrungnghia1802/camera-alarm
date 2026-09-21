package com.personal.cameraalarm.ui.settings

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.schedule.ActiveTimeRange
import com.personal.cameraalarm.schedule.ScheduleMode
import com.personal.cameraalarm.ui.AppScreen
import com.personal.cameraalarm.ui.navigation.AppBottomNavigationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onNavigate: ((AppScreen) -> Unit)? = null,
    onNavigateToSoundPicker: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showResetDefaultsDialog by remember { mutableStateOf(false) }
    var showAddRangeDialog by remember { mutableStateOf(false) }
    var editingRange by remember { mutableStateOf<ActiveTimeRange?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedSound = AlarmSoundCatalog.resolve(state.draftSettings.alarmSoundKey)
    val savedSuccessText = stringResource(R.string.settings_saved_success)

    BackHandler {
        if (state.isModified) {
            showUnsavedDialog = true
        } else {
            onBack?.invoke()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar(savedSuccessText)
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

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.dialog_unsaved_title)) },
            text = { Text(stringResource(R.string.dialog_unsaved_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveSettings {
                            showUnsavedDialog = false
                            val nav = pendingNavigation
                            pendingNavigation = null
                            if (nav != null) nav.invoke() else onBack?.invoke()
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        pendingNavigation = null
                    }) {
                        Text(stringResource(R.string.btn_stay))
                    }
                    TextButton(
                        onClick = {
                            viewModel.discardChanges()
                            showUnsavedDialog = false
                            val nav = pendingNavigation
                            pendingNavigation = null
                            if (nav != null) nav.invoke() else onBack?.invoke()
                        }
                    ) {
                        Text(stringResource(R.string.btn_discard), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showResetDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showResetDefaultsDialog = false },
            title = { Text(stringResource(R.string.dialog_reset_defaults_title)) },
            text = { Text(stringResource(R.string.dialog_reset_defaults_message)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetDefaults()
                    showResetDefaultsDialog = false
                }) { Text(stringResource(R.string.btn_reset_defaults)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDefaultsDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.title_settings), fontWeight = FontWeight.Bold)
                        if (state.isModified) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_unsaved_badge),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveSettings() },
                        enabled = state.isModified,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentScreen = AppScreen.SETTINGS,
                onNavigate = { target ->
                    if (state.isModified) {
                        pendingNavigation = { onNavigate?.invoke(target) }
                        showUnsavedDialog = true
                    } else {
                        onNavigate?.invoke(target)
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
            Text(
                stringResource(R.string.section_basic),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Sound Picker Navigation Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSoundPicker),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.section_sound),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(selectedSound.displayNameResId),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Schedule Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.section_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.desc_schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.draftSettings.scheduleMode == ScheduleMode.ALWAYS_ACTIVE,
                            onClick = { viewModel.setScheduleMode(ScheduleMode.ALWAYS_ACTIVE) },
                            label = { Text(stringResource(R.string.schedule_mode_always)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.draftSettings.scheduleMode == ScheduleMode.CUSTOM,
                            onClick = { viewModel.setScheduleMode(ScheduleMode.CUSTOM) },
                            label = { Text(stringResource(R.string.schedule_mode_custom)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state.draftSettings.scheduleMode == ScheduleMode.CUSTOM) {
                        HorizontalDivider()

                        val ranges = state.draftSettings.scheduleRanges
                        val enabledCount = ranges.count { it.enabled }

                        if (ranges.isEmpty() || enabledCount == 0) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
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
                                        text = stringResource(R.string.schedule_no_ranges),
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
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "${range.formatStart()} → ${range.formatEnd()}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (range.enabled) FontWeight.Bold else FontWeight.Normal,
                                        color = if (range.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (range.startMinutes > range.endMinutes) {
                                        Text(
                                            text = stringResource(R.string.schedule_overnight_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (range.startMinutes == range.endMinutes) {
                                        Text(
                                            text = stringResource(R.string.schedule_24h_badge),
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
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit))
                                    }
                                    IconButton(onClick = { viewModel.deleteScheduleRange(range.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
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
                            Text(stringResource(R.string.schedule_add_range))
                        }
                    }
                }
            }

            // Alarm Delay Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.section_alarm_delay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.desc_alarm_delay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0L to "0s", 1000L to "1s", 3000L to "3s", 5000L to "5s").forEach { (ms, label) ->
                            FilterChip(
                                selected = state.draftSettings.alarmDelayMs == ms,
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.section_cooldown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.desc_cooldown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var isEditingCooldown by remember { mutableStateOf(false) }
                    var cooldownSecText by remember(state.settings.cooldownMs) {
                        mutableStateOf((state.draftSettings.cooldownMs / 1000L).toString())
                    }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    val focusRequester = remember { FocusRequester() }
                    val enteredSec = cooldownSecText.toLongOrNull() ?: 0L

                    LaunchedEffect(isEditingCooldown) {
                        if (isEditingCooldown) {
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cooldownSecText,
                            onValueChange = { input ->
                                if (isEditingCooldown) {
                                    val digits = input.filter { it.isDigit() }.take(6)
                                    cooldownSecText = digits
                                    val sec = digits.toLongOrNull() ?: 0L
                                    viewModel.setCooldown(sec * 1000L)
                                }
                            },
                            enabled = isEditingCooldown,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            label = { Text(stringResource(R.string.cooldown_input_label)) },
                            placeholder = { Text(stringResource(R.string.cooldown_input_placeholder)) },
                            suffix = { Text(stringResource(R.string.cooldown_seconds_unit)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )

                        if (isEditingCooldown) {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    val sec = cooldownSecText.toLongOrNull() ?: 0L
                                    viewModel.saveCooldown(sec * 1000L)
                                    isEditingCooldown = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.btn_save_cooldown),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isEditingCooldown = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.btn_edit_cooldown),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Duration conversion explanation
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatCooldownExplanation(enteredSec),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Basic behavior
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.section_behavior), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(stringResource(R.string.setting_vibration), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.desc_vibration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.draftSettings.vibrationEnabled,
                            onCheckedChange = { viewModel.setVibration(it) }
                        )
                    }

                }
            }

            // Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.section_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.desc_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.draftSettings.language == "vi",
                            onClick = { viewModel.setLanguage("vi") },
                            label = { Text(stringResource(R.string.lang_vietnamese), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.draftSettings.language == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text(stringResource(R.string.lang_english), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Advanced Settings Section (Diagnostics & Clear History)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.section_advanced), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.setting_fullscreen), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.desc_fullscreen),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.draftSettings.fullScreenEnabled,
                            onCheckedChange = { viewModel.setFullScreen(it) }
                        )
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_open_diagnostics), textAlign = TextAlign.Center)
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_clear_history, state.historyCount),
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = { showResetDefaultsDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_reset_defaults), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRangeEditDialog(
    initialRange: ActiveTimeRange?,
    onDismiss: () -> Unit,
    onSave: (startMinutes: Int, endMinutes: Int) -> Unit
) {
    val context = LocalContext.current
    var startHour by remember { mutableIntStateOf(initialRange?.let { it.startMinutes / 60 } ?: 23) }
    var startMinute by remember { mutableIntStateOf(initialRange?.let { it.startMinutes % 60 } ?: 0) }
    var endHour by remember { mutableIntStateOf(initialRange?.let { it.endMinutes / 60 } ?: 7) }
    var endMinute by remember { mutableIntStateOf(initialRange?.let { it.endMinutes % 60 } ?: 0) }

    var startHourStr by remember { mutableStateOf("%02d".format(startHour)) }
    var startMinuteStr by remember { mutableStateOf("%02d".format(startMinute)) }
    var endHourStr by remember { mutableStateOf("%02d".format(endHour)) }
    var endMinuteStr by remember { mutableStateOf("%02d".format(endMinute)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialRange != null) stringResource(R.string.dialog_edit_time_range)
                else stringResource(R.string.dialog_add_time_range)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.dialog_time_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Start Time Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.time_start), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startHourStr,
                            onValueChange = { str ->
                                val clean = str.filter { it.isDigit() }.take(2)
                                startHourStr = clean
                                clean.toIntOrNull()?.let { if (it in 0..23) startHour = it }
                            },
                            label = { Text(stringResource(R.string.time_hour)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        OutlinedTextField(
                            value = startMinuteStr,
                            onValueChange = { str ->
                                val clean = str.filter { it.isDigit() }.take(2)
                                startMinuteStr = clean
                                clean.toIntOrNull()?.let { if (it in 0..59) startMinute = it }
                            },
                            label = { Text(stringResource(R.string.time_minute)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        startHour = h
                                        startMinute = m
                                        startHourStr = "%02d".format(h)
                                        startMinuteStr = "%02d".format(m)
                                    },
                                    startHour,
                                    startMinute,
                                    true
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = stringResource(R.string.time_select_time))
                        }
                    }
                }

                // End Time Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.time_end), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = endHourStr,
                            onValueChange = { str ->
                                val clean = str.filter { it.isDigit() }.take(2)
                                endHourStr = clean
                                clean.toIntOrNull()?.let { if (it in 0..23) endHour = it }
                            },
                            label = { Text(stringResource(R.string.time_hour)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        OutlinedTextField(
                            value = endMinuteStr,
                            onValueChange = { str ->
                                val clean = str.filter { it.isDigit() }.take(2)
                                endMinuteStr = clean
                                clean.toIntOrNull()?.let { if (it in 0..59) endMinute = it }
                            },
                            label = { Text(stringResource(R.string.time_minute)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        endHour = h
                                        endMinute = m
                                        endHourStr = "%02d".format(h)
                                        endMinuteStr = "%02d".format(m)
                                    },
                                    endHour,
                                    endMinute,
                                    true
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = stringResource(R.string.time_select_time))
                        }
                    }
                }

                val currentStartMinutes = startHour * 60 + startMinute
                val currentEndMinutes = endHour * 60 + endMinute

                if (currentStartMinutes > currentEndMinutes) {
                    Text(
                        text = stringResource(R.string.time_overnight_info, startHour, startMinute, endHour, endMinute),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (currentStartMinutes == currentEndMinutes) {
                    Text(
                        text = stringResource(R.string.time_24h_info),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val startM = (startHourStr.toIntOrNull()?.coerceIn(0, 23) ?: startHour) * 60 +
                        (startMinuteStr.toIntOrNull()?.coerceIn(0, 59) ?: startMinute)
                val endM = (endHourStr.toIntOrNull()?.coerceIn(0, 23) ?: endHour) * 60 +
                        (endMinuteStr.toIntOrNull()?.coerceIn(0, 59) ?: endMinute)
                onSave(startM, endM)
            }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun formatCooldownExplanation(seconds: Long): String {
    if (seconds <= 0L) {
        return stringResource(R.string.cooldown_zero_explanation)
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remSecs = seconds % 60
    return when {
        hours > 0 -> {
            if (minutes > 0 && remSecs > 0) {
                stringResource(R.string.cooldown_format_hours_mins_secs, hours, minutes, remSecs, seconds)
            } else if (minutes > 0) {
                stringResource(R.string.cooldown_format_hours_mins, hours, minutes, seconds)
            } else if (remSecs > 0) {
                stringResource(R.string.cooldown_format_hours_secs, hours, remSecs, seconds)
            } else {
                stringResource(R.string.cooldown_format_hours, hours, seconds)
            }
        }
        minutes > 0 -> {
            if (remSecs > 0) {
                stringResource(R.string.cooldown_format_mins_secs, minutes, remSecs, seconds)
            } else {
                stringResource(R.string.cooldown_format_mins, minutes, seconds)
            }
        }
        else -> {
            stringResource(R.string.cooldown_format_secs, seconds)
        }
    }
}
