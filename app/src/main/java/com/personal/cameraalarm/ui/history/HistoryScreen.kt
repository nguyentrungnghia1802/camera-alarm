package com.personal.cameraalarm.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.data.history.AlertEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_history), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_cancel))
                    }
                },
                actions = {
                    if (state.events.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.btn_clear))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    val labelRes = when (filter) {
                        HistoryFilter.ALL -> R.string.history_filter_all
                        HistoryFilter.TRIGGERED -> R.string.history_filter_triggered
                        HistoryFilter.SUPPRESSED -> R.string.history_filter_suppressed
                        HistoryFilter.ERRORS -> R.string.history_filter_errors
                    }
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            if (state.events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.events, key = { it.id }) { event ->
                        HistoryItemCard(event = event)
                    }
                }
            }

            // Pagination Controls
            if (state.totalCount > 0) {
                val startItem = ((state.currentPage - 1) * com.personal.cameraalarm.data.history.HistoryRepository.PAGE_SIZE) + 1
                val endItem = minOf(state.currentPage * com.personal.cameraalarm.data.history.HistoryRepository.PAGE_SIZE, state.totalCount)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewModel.previousPage() },
                            enabled = state.currentPage > 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_prev_page))
                        }

                        Text(
                            text = stringResource(
                                R.string.history_page_format,
                                state.currentPage,
                                state.totalPages,
                                startItem,
                                endItem,
                                state.totalCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        IconButton(
                            onClick = { viewModel.nextPage() },
                            enabled = state.currentPage < state.totalPages
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.btn_next_page))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(event: AlertEventEntity) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss • dd/MM/yyyy", Locale.getDefault()) }
    val formattedTime = remember(event.createdAtEpochMs) { timeFormat.format(Date(event.createdAtEpochMs)) }

    val (badgeColor, textColor, localizedDecision) = when (event.decision) {
        "SCHEDULED", "ALARM_FIRED" -> Triple(Color(0xFF2E7D32), Color.White, stringResource(R.string.decision_scheduled))
        "SUPPRESSED_PENDING" -> Triple(Color(0xFFEF6C00), Color.White, stringResource(R.string.decision_suppressed_pending))
        "SUPPRESSED_RINGING" -> Triple(Color(0xFFEF6C00), Color.White, stringResource(R.string.decision_suppressed_ringing))
        "SUPPRESSED_COOLDOWN" -> Triple(Color(0xFFEF6C00), Color.White, stringResource(R.string.decision_suppressed_cooldown))
        "SUPPRESSED_OUTSIDE_ACTIVE_HOURS" -> Triple(Color(0xFF0288D1), Color.White, stringResource(R.string.decision_suppressed_outside_hours))
        "IGNORED_NO_RULE_MATCH" -> Triple(Color(0xFF757575), Color.White, stringResource(R.string.decision_ignored_no_match))
        "IGNORED_WRONG_PACKAGE" -> Triple(Color(0xFF757575), Color.White, stringResource(R.string.decision_ignored_wrong_package))
        "IGNORED_DUPLICATE" -> Triple(Color(0xFF757575), Color.White, stringResource(R.string.decision_ignored_duplicate))
        "IGNORED_MONITORING_OFF" -> Triple(Color(0xFF757575), Color.White, stringResource(R.string.decision_ignored_monitoring_off))
        else -> Triple(Color(0xFF757575), Color.White, event.decision)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = localizedDecision,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = event.sourcePackage ?: "Ứng dụng camera",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!event.title.isNullOrBlank()) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!event.textPreview.isNullOrBlank()) {
                Text(
                    text = event.textPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider()
                    event.ruleId?.let {
                        Text("Mã quy tắc: $it", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    event.details?.let {
                        Text("Chi tiết: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
