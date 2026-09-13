package com.personal.cameraalarm.ui.diagnostics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.permission.ReadinessRepository

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
                title = { Text("Device Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.copyDiagnostics(context, info) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Diagnostics")
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
            // Environment Card
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
                    Text("Environment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DiagRow("App Version", info.appVersion)
                    DiagRow("Android Version", "API ${info.sdkInt} (Android ${android.os.Build.VERSION.RELEASE})")
                    DiagRow("Device", info.deviceModel)
                    DiagRow("Manufacturer / Brand", "${info.manufacturer} / ${info.brand}")
                    DiagRow("Xiaomi Advisor", if (info.isXiaomiFamily) "Detected (Active)" else "Generic Android")
                }
            }

            // Permissions Card
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
                    Text("Permissions & Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    DiagStatusItem(
                        title = "Notification Access",
                        ok = info.readiness.notificationAccessGranted,
                        detail = if (info.readiness.notificationAccessGranted) "Granted" else "Required",
                        actionLabel = "Settings",
                        onAction = { context.startActivity(readinessRepo.notificationAccessSettingsIntent()) }
                    )

                    DiagStatusItem(
                        title = "Listener Connection",
                        ok = info.readiness.listenerConnected,
                        detail = if (info.readiness.listenerConnected) "Connected" else "Disconnected",
                        actionLabel = null,
                        onAction = null
                    )

                    DiagStatusItem(
                        title = "Exact Alarm",
                        ok = info.readiness.exactAlarmGranted,
                        detail = if (info.readiness.exactAlarmGranted) "Granted" else "Required",
                        actionLabel = "Grant",
                        onAction = { readinessRepo.exactAlarmSettingsIntent()?.let { context.startActivity(it) } }
                    )

                    DiagStatusItem(
                        title = "App Notifications",
                        ok = info.readiness.postNotificationsGranted,
                        detail = if (info.readiness.postNotificationsGranted) "Granted" else "Denied",
                        actionLabel = "Settings",
                        onAction = { context.startActivity(readinessRepo.appNotificationSettingsIntent()) }
                    )

                    DiagStatusItem(
                        title = "Full-Screen Intent",
                        ok = info.fullScreenIntentAllowed,
                        detail = if (info.fullScreenIntentAllowed) "Allowed" else "Disallowed",
                        actionLabel = if (!info.fullScreenIntentAllowed && readinessRepo.fullScreenIntentSettingsIntent() != null) "Settings" else null,
                        onAction = { readinessRepo.fullScreenIntentSettingsIntent()?.let { context.startActivity(it) } }
                    )

                    DiagStatusItem(
                        title = "Alarm Stream Volume",
                        ok = info.volumeStatus.isNonZero,
                        detail = "Current: ${info.volumeStatus.current} (Min: ${info.volumeStatus.minimum}, Max: ${info.volumeStatus.maximum})",
                        actionLabel = null,
                        onAction = null
                    )
                }
            }

            // Xiaomi / OEM Reliability Card
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
                        text = if (info.isXiaomiFamily) "Xiaomi / HyperOS Reliability" else "Device Background Reliability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (info.isXiaomiFamily) "Configure OEM background, autostart, and battery settings for uninterrupted alerts."
                        else "OEM-specific background settings are not required on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val items = viewModel.reliabilityAdvisor.getReliabilityItems(context)
                    items.filter { it.isOemSpecific }.forEach { item ->
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
                                            com.personal.cameraalarm.reliability.ReliabilityStatus.READY -> Icons.Default.CheckCircle
                                            com.personal.cameraalarm.reliability.ReliabilityStatus.USER_CONFIRMATION_REQUIRED -> Icons.Default.Warning
                                            com.personal.cameraalarm.reliability.ReliabilityStatus.MISSING -> Icons.Default.Cancel
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when (item.status) {
                                            com.personal.cameraalarm.reliability.ReliabilityStatus.READY -> Color(0xFF2E7D32)
                                            com.personal.cameraalarm.reliability.ReliabilityStatus.USER_CONFIRMATION_REQUIRED -> Color(0xFFEF6C00)
                                            com.personal.cameraalarm.reliability.ReliabilityStatus.MISSING -> Color(0xFFC62828)
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

            // Core State Card
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
                    Text("Core Alarm State", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DiagRow("Current Alarm State", info.alarmState)
                    DiagRow("Monitoring Enabled", if (info.monitoringEnabled) "Yes" else "No")
                    DiagRow("Active Source Package", info.sourcePackage ?: "None configured")
                    DiagRow("Active Enabled Rules", "${info.enabledRuleCount} rules")
                    DiagRow("Last Error", info.lastRuntimeError ?: "None")
                }
            }

            // Action Buttons
            Button(
                onClick = { viewModel.copyDiagnostics(context, info) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Diagnostics to Clipboard")
            }

            OutlinedButton(
                onClick = { viewModel.startTestAlarm(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Test Alarm")
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
