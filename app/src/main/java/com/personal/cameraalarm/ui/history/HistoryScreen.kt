package com.personal.cameraalarm.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.personal.cameraalarm.ui.AppScreen
import com.personal.cameraalarm.ui.navigation.AppBottomNavigationBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: (() -> Unit)? = null,
    onNavigate: ((AppScreen) -> Unit)? = null
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
                actions = {
                    if (state.events.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.btn_clear))
                        }
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentScreen = AppScreen.HISTORY,
                onNavigate = { onNavigate?.invoke(it) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewModel.previousPage() },
                            enabled = state.currentPage > 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_prev_page))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.history_page_number, state.currentPage, state.totalPages),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.history_page_range, startItem, endItem, state.totalCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

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

    val (badgeText, badgeColor) = when (event.decision) {
        "SCHEDULED", "ALARM_FIRED" -> Pair(stringResource(R.string.history_badge_alarm), Color(0xFF2E7D32))
        "SUPPRESSED_PENDING", "SUPPRESSED_RINGING", "SUPPRESSED_COOLDOWN", "SUPPRESSED_OUTSIDE_ACTIVE_HOURS" -> Pair(stringResource(R.string.history_badge_suppressed), Color(0xFFEF6C00))
        "IGNORED_NO_RULE_MATCH", "IGNORED_WRONG_PACKAGE", "IGNORED_DUPLICATE", "IGNORED_MONITORING_OFF" -> Pair(stringResource(R.string.history_badge_ignored), Color(0xFF757575))
        else -> Pair(stringResource(R.string.history_badge_error), Color(0xFFC62828))
    }

    val reasonText = when (event.decision) {
        "SCHEDULED", "ALARM_FIRED" -> stringResource(R.string.decision_scheduled)
        "SUPPRESSED_PENDING" -> stringResource(R.string.decision_suppressed_pending)
        "SUPPRESSED_RINGING" -> stringResource(R.string.decision_suppressed_ringing)
        "SUPPRESSED_COOLDOWN" -> stringResource(R.string.decision_suppressed_cooldown_reason)
        "SUPPRESSED_OUTSIDE_ACTIVE_HOURS" -> stringResource(R.string.decision_suppressed_outside_hours)
        "IGNORED_NO_RULE_MATCH" -> stringResource(R.string.decision_ignored_no_match)
        "IGNORED_WRONG_PACKAGE" -> stringResource(R.string.decision_ignored_wrong_package)
        "IGNORED_DUPLICATE" -> stringResource(R.string.decision_ignored_duplicate)
        "IGNORED_MONITORING_OFF" -> stringResource(R.string.decision_ignored_monitoring_off)
        else -> event.decision
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = event.sourcePackage ?: stringResource(R.string.setup_source_app),
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

            Text(
                text = stringResource(R.string.history_reason_prefix, reasonText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (event.decision == "SUPPRESSED_COOLDOWN" && !event.details.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.decision_suppressed_cooldown_reason),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = event.details,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF6C00)
                        )
                    }
                }
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
                        Text(stringResource(R.string.history_rule_id_prefix, it), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    if (event.decision != "SUPPRESSED_COOLDOWN") {
                        event.details?.let {
                            Text(stringResource(R.string.history_details_prefix, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
