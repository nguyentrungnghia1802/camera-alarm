package com.personal.cameraalarm.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedDraftKey by remember(state.settings.alarmSoundKey) {
        mutableStateOf(state.draftSettings.alarmSoundKey)
    }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val isModified = selectedDraftKey != state.settings.alarmSoundKey

    val selectedSound = AlarmSoundCatalog.resolve(selectedDraftKey)
    val isCurrentSoundPreviewing = state.previewPlayingKey == selectedSound.key

    BackHandler {
        if (isModified) showUnsavedDialog = true else onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreview()
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.dialog_unsaved_title)) },
            text = { Text(stringResource(R.string.dialog_unsaved_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveAlarmSound(selectedDraftKey) {
                            showUnsavedDialog = false
                            onBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text(stringResource(R.string.btn_stay))
                    }
                    TextButton(
                        onClick = {
                            selectedDraftKey = state.settings.alarmSoundKey
                            viewModel.selectAlarmSound(state.settings.alarmSoundKey)
                            showUnsavedDialog = false
                            onBack()
                        }
                    ) {
                        Text(stringResource(R.string.btn_discard), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.title_sound_picker), fontWeight = FontWeight.Bold)
                        if (isModified) {
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
                navigationIcon = {
                    IconButton(onClick = {
                        if (isModified) showUnsavedDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_cancel))
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveAlarmSound(selectedDraftKey) {
                                onBack()
                            }
                        },
                        enabled = isModified,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Sound Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.current_sound_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = stringResource(selectedSound.displayNameResId),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Button(
                            onClick = {
                                if (isCurrentSoundPreviewing) viewModel.stopPreview()
                                else viewModel.playPreview(selectedSound)
                            },
                            colors = if (isCurrentSoundPreviewing) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (isCurrentSoundPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCurrentSoundPreviewing) stringResource(R.string.sound_preview_stop)
                                else stringResource(R.string.sound_preview_play),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Sound List Section
            item {
                Text(
                    text = stringResource(R.string.sound_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(viewModel.availableSounds) { sound ->
                val isSelected = selectedDraftKey == sound.key
                val isThisPreviewing = state.previewPlayingKey == sound.key

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDraftKey = sound.key
                            viewModel.selectAlarmSound(sound.key)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedDraftKey = sound.key
                                    viewModel.selectAlarmSound(sound.key)
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(sound.displayNameResId),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (isThisPreviewing) viewModel.stopPreview()
                                else viewModel.playPreview(sound)
                            }
                        ) {
                            Icon(
                                if (isThisPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isThisPreviewing) stringResource(R.string.sound_preview_stop) else stringResource(R.string.sound_preview_play),
                                tint = if (isThisPreviewing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
