package com.personal.cameraalarm.ui.main

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.schedule.ScheduleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    readinessRepo: ReadinessRepository,
    onNavigateToSourcePicker: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSoundPicker: () -> Unit
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
        },
        bottomBar = {
            com.personal.cameraalarm.ui.navigation.AppBottomNavigationBar(
                currentScreen = com.personal.cameraalarm.ui.AppScreen.DASHBOARD,
                onNavigate = { screen ->
                    when (screen) {
                        com.personal.cameraalarm.ui.AppScreen.DASHBOARD -> {}
                        com.personal.cameraalarm.ui.AppScreen.RULES -> onNavigateToRules()
                        com.personal.cameraalarm.ui.AppScreen.HISTORY -> onNavigateToHistory()
                        com.personal.cameraalarm.ui.AppScreen.SETTINGS -> onNavigateToSettings()
                        else -> {}
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
            // Status & Quick Summary Card
            StatusCard(
                state = state,
                onNavigateToSourcePicker = onNavigateToSourcePicker,
                onNavigateToSoundPicker = onNavigateToSoundPicker,
                onNavigateToSettings = onNavigateToSettings
            )

            // Ringing / Alarming Banner with STOP button
            if (state.isRinging) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.alarm_active_banner),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.stopAlarm(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_stop_alarm),
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

            // Setup Checklist Card
            SetupChecklistCard(
                state = state,
                context = context,
                readinessRepo = readinessRepo,
                onNavigateToSourcePicker = onNavigateToSourcePicker,
                onNavigateToRules = onNavigateToRules
            )

            // Alarm Stream Volume Warning (if 0)
            if (!state.readiness.alarmVolumeNonZero) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.warning_volume_zero),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Quick Test Alarm Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (state.isRinging) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isRinging) stringResource(R.string.btn_stop_alarm)
                            else stringResource(R.string.btn_test_alarm),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.btn_test_alarm_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: MainUiState,
    onNavigateToSourcePicker: () -> Unit,
    onNavigateToSoundPicker: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val (statusText, badgeColor, textColor) = when (state.effectiveStatus) {
        AppStatus.READY -> Triple(stringResource(R.string.status_ready), Color(0xFF2E7D32), Color.White)
        AppStatus.STANDBY -> Triple(stringResource(R.string.status_standby), Color(0xFF0288D1), Color.White)
        AppStatus.NEEDS_SETUP -> Triple(stringResource(R.string.status_needs_setup), Color(0xFFEF6C00), Color.White)
        AppStatus.ALARMING -> Triple(stringResource(R.string.status_alarming), Color(0xFFC62828), Color.White)
    }

    val selectedSound = AlarmSoundCatalog.resolve(null)
    val soundDisplayName = AlarmSoundCatalog.allSounds
        .firstOrNull { it.displayName == state.alarmSoundName || it.key == state.alarmSoundName }
        ?.let { stringResource(it.displayNameResId) }
        ?: state.alarmSoundName

    val scheduleSummary = when {
        state.scheduleMode == ScheduleMode.ALWAYS_ACTIVE -> stringResource(R.string.schedule_always_active)
        state.activeScheduleRangesCount == 0 -> stringResource(R.string.schedule_no_ranges_summary)
        state.activeScheduleRangesCount == 1 -> state.singleScheduleRangeSummary ?: stringResource(R.string.schedule_summary_count, 1)
        else -> stringResource(R.string.schedule_summary_count, state.activeScheduleRangesCount)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.system_status_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            state.effectiveStatus == AppStatus.STANDBY -> stringResource(R.string.monitoring_standby)
                            state.monitoringEnabled -> stringResource(R.string.monitoring_active)
                            else -> stringResource(R.string.monitoring_inactive)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = statusText,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider()

            // Summary Information
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(
                    label = stringResource(R.string.info_camera_label),
                    value = state.sourceLabel ?: state.sourcePackage ?: stringResource(R.string.camera_not_selected),
                    onClick = onNavigateToSourcePicker
                )

                InfoRow(
                    label = stringResource(R.string.info_sound_label),
                    value = soundDisplayName,
                    onClick = onNavigateToSoundPicker
                )

                InfoRow(
                    label = stringResource(R.string.info_schedule_label),
                    value = scheduleSummary,
                    onClick = onNavigateToSettings
                )
            }

            if (state.effectiveStatus == AppStatus.STANDBY) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.standby_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.nextActiveTime?.let {
                        Text(
                            text = stringResource(R.string.next_active_time, it),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Row(
            modifier = Modifier.weight(0.62f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_monitoring),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (blockingReady) stringResource(R.string.desc_monitoring_ready)
                    else stringResource(R.string.desc_monitoring_not_ready),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.section_setup),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 1. Notification Access
            ChecklistItem(
                title = stringResource(R.string.setup_notif_access),
                isOk = state.readiness.notificationAccessGranted,
                statusText = if (state.readiness.notificationAccessGranted) stringResource(R.string.status_granted)
                else stringResource(R.string.status_required),
                buttonText = if (!state.readiness.notificationAccessGranted) stringResource(R.string.btn_grant) else null,
                onAction = { context.startActivity(readinessRepo.notificationAccessSettingsIntent()) }
            )

            // 2. Exact Alarm
            ChecklistItem(
                title = stringResource(R.string.setup_exact_alarm),
                isOk = state.readiness.exactAlarmGranted,
                statusText = if (state.readiness.exactAlarmGranted) stringResource(R.string.status_granted)
                else stringResource(R.string.status_required),
                buttonText = if (!state.readiness.exactAlarmGranted) stringResource(R.string.btn_grant) else null,
                onAction = { readinessRepo.exactAlarmSettingsIntent()?.let { context.startActivity(it) } }
            )

            // 3. App Notifications
            ChecklistItem(
                title = stringResource(R.string.setup_post_notif),
                isOk = state.readiness.postNotificationsGranted,
                statusText = if (state.readiness.postNotificationsGranted) stringResource(R.string.status_granted)
                else stringResource(R.string.status_required),
                buttonText = if (!state.readiness.postNotificationsGranted) stringResource(R.string.btn_grant) else null,
                onAction = { context.startActivity(readinessRepo.appNotificationSettingsIntent()) }
            )

            // 4. Source App
            ChecklistItem(
                title = stringResource(R.string.setup_source_app),
                isOk = state.readiness.sourceConfigured,
                statusText = state.sourceLabel ?: state.sourcePackage ?: stringResource(R.string.camera_not_selected),
                buttonText = stringResource(R.string.btn_select),
                onAction = onNavigateToSourcePicker
            )

            // 5. Trigger Rules
            ChecklistItem(
                title = stringResource(R.string.setup_trigger_rules),
                isOk = state.readiness.ruleConfigured,
                statusText = if (state.enabledRuleCount > 0) stringResource(R.string.status_rules_count, state.enabledRuleCount)
                else stringResource(R.string.status_rules_none),
                buttonText = stringResource(R.string.btn_rules),
                onAction = onNavigateToRules
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
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Icon(
                if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOk) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (buttonText != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 12.sp,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
