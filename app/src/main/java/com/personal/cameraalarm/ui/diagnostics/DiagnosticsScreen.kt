package com.personal.cameraalarm.ui.diagnostics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.reliability.DeviceReliabilityAdvisor
import com.personal.cameraalarm.reliability.ReliabilityStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    readinessRepo: ReadinessRepository,
    onBack: () -> Unit
) {
    val statePair by viewModel.uiState.collectAsState()
    val info = statePair.first
    val msg = statePair.second
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        msg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCopyMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_diagnostics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.copyDiagnostics(context, info) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.diag_copy_btn))
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
            // 1. Environment Card
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
                    Text(stringResource(R.string.diag_environment), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DiagRow(stringResource(R.string.diag_app_version), info.appVersion)
                    DiagRow(stringResource(R.string.diag_android_version), "API ${info.sdkInt} (Android ${android.os.Build.VERSION.RELEASE})")
                    DiagRow(stringResource(R.string.diag_device), info.deviceModel)
                    DiagRow(stringResource(R.string.diag_manufacturer), "${info.manufacturer} / ${info.brand}")
                    DiagRow("Hệ điều hành nhận diện", info.activeOemName)
                }
            }

            // 2. Standard Android Permissions Card
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
                    Text(stringResource(R.string.diag_permissions_services), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    DiagStatusItem(
                        title = stringResource(R.string.diag_notif_access),
                        ok = info.readiness.notificationAccessGranted,
                        detail = if (info.readiness.notificationAccessGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_required),
                        actionLabel = stringResource(R.string.btn_open_settings),
                        onAction = { context.startActivity(readinessRepo.notificationAccessSettingsIntent()) }
                    )

                    DiagStatusItem(
                        title = stringResource(R.string.diag_listener_conn),
                        ok = info.readiness.listenerConnected,
                        detail = if (info.readiness.listenerConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                        actionLabel = null,
                        onAction = null
                    )

                    DiagStatusItem(
                        title = stringResource(R.string.diag_exact_alarm),
                        ok = info.readiness.exactAlarmGranted,
                        detail = if (info.readiness.exactAlarmGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_required),
                        actionLabel = stringResource(R.string.btn_grant),
                        onAction = { readinessRepo.exactAlarmSettingsIntent()?.let { context.startActivity(it) } }
                    )

                    DiagStatusItem(
                        title = stringResource(R.string.diag_app_notif),
                        ok = info.readiness.postNotificationsGranted,
                        detail = if (info.readiness.postNotificationsGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_required),
                        actionLabel = stringResource(R.string.btn_open_settings),
                        onAction = { context.startActivity(readinessRepo.appNotificationSettingsIntent()) }
                    )

                    DiagStatusItem(
                        title = stringResource(R.string.diag_fullscreen),
                        ok = info.fullScreenIntentAllowed,
                        detail = if (info.fullScreenIntentAllowed) stringResource(R.string.diag_allowed) else stringResource(R.string.diag_disallowed),
                        actionLabel = if (!info.fullScreenIntentAllowed && readinessRepo.fullScreenIntentSettingsIntent() != null) stringResource(R.string.btn_open_settings) else null,
                        onAction = { readinessRepo.fullScreenIntentSettingsIntent()?.let { context.startActivity(it) } }
                    )

                    // Battery Optimization Item
                    val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
                    DiagStatusItem(
                        title = stringResource(R.string.diag_battery_unrestricted),
                        ok = isIgnoringBattery,
                        detail = if (isIgnoringBattery) stringResource(R.string.diag_battery_status_ok) else stringResource(R.string.diag_battery_status_needed),
                        actionLabel = if (!isIgnoringBattery) stringResource(R.string.btn_grant) else null,
                        onAction = {
                            try {
                                context.startActivity(DeviceReliabilityAdvisor.getBatteryOptimizationIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        }
                    )

                    DiagStatusItem(
                        title = stringResource(R.string.diag_volume),
                        ok = info.volumeStatus.isNonZero,
                        detail = stringResource(R.string.diag_volume_detail, info.volumeStatus.current, info.volumeStatus.minimum, info.volumeStatus.maximum),
                        actionLabel = null,
                        onAction = null
                    )
                }
            }

            // 3. Multi-OEM Reliability Guidance Card
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
                        text = stringResource(R.string.diag_oem_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.diag_oem_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // OEM Selector Horizontal Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.advisorRegistry.allAdvisors.forEach { advisor ->
                            val isSelected = info.selectedOemKey == advisor.oemKey
                            val isDeviceMatch = advisor.isApplicable
                            val shortName = advisor.deviceFamilyName.substringBefore("(").substringBefore("/").trim()
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectOem(advisor.oemKey) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(shortName)
                                        if (isDeviceMatch) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "★",
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE65100),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    val currentAdvisor = viewModel.currentAdvisor()
                    val oemItems = currentAdvisor.getOemItems(context)

                    if (oemItems.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Thiết bị này tuân theo chuẩn Android AOSP gốc. Chỉ cần đảm bảo đã cấp đủ 5 quyền ở mục 'Quyền & Dịch vụ' phía trên để ứng dụng hoạt động ổn định nhất.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        oemItems.forEach { item ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                            when (item.status) {
                                                ReliabilityStatus.READY -> Icons.Default.CheckCircle
                                                ReliabilityStatus.USER_CONFIRMATION_REQUIRED -> Icons.Default.Warning
                                                ReliabilityStatus.MISSING -> Icons.Default.Cancel
                                                else -> Icons.Default.Info
                                            },
                                            contentDescription = null,
                                            tint = when (item.status) {
                                                ReliabilityStatus.READY -> Color(0xFF2E7D32)
                                                ReliabilityStatus.USER_CONFIRMATION_REQUIRED -> Color(0xFFEF6C00)
                                                ReliabilityStatus.MISSING -> Color(0xFFC62828)
                                                else -> Color(0xFF757575)
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = item.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (item.actionLabel != null && item.actionIntent != null) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(item.actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                } catch (_: Exception) {
                                                    try {
                                                        context.startActivity(
                                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                data = Uri.fromParts("package", context.packageName, null)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                        )
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(item.actionLabel, fontSize = 11.sp)
                                        }
                                    }
                                }

                                item.userInstruction?.let { instruction ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 28.dp, top = 2.dp)
                                    ) {
                                        Text(
                                            text = instruction,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(6.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            // 4. Core State Card
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
                    Text(stringResource(R.string.diag_core_state), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DiagRow(stringResource(R.string.diag_alarm_state), info.alarmState)
                    DiagRow(stringResource(R.string.diag_monitoring_enabled), if (info.monitoringEnabled) stringResource(R.string.diag_yes) else stringResource(R.string.diag_no))
                    DiagRow(stringResource(R.string.diag_active_source), info.sourcePackage ?: stringResource(R.string.diag_none_configured))
                    DiagRow(stringResource(R.string.diag_active_rules), stringResource(R.string.diag_rules_count_format, info.enabledRuleCount))
                    DiagRow(stringResource(R.string.diag_last_error), info.lastRuntimeError ?: stringResource(R.string.diag_none))
                }
            }

            // 5. Action Buttons
            Button(
                onClick = { viewModel.copyDiagnostics(context, info) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.diag_copy_btn))
            }

            OutlinedButton(
                onClick = { viewModel.startTestAlarm(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.diag_run_test_btn))
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DiagStatusItem(
    title: String,
    ok: Boolean,
    detail: String,
    actionLabel: String?,
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
                if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp)
            }
        }
    }
}
