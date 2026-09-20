package com.personal.cameraalarm.ui.rule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.trigger.MatchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    viewModel: RuleViewModel,
    onBack: () -> Unit,
    onSelectSourceApp: () -> Unit
) {
    val state by viewModel.editorState.collectAsState()
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showKeywordDialog by remember { mutableStateOf(false) }
    var keywordDialogIndex by remember { mutableStateOf<Int?>(null) }
    var keywordDraft by remember { mutableStateOf("") }

    val isModified = remember(state.name, state.keywordsRaw, state.sourcePackage, state.matchMode, state.priority, state.enabled) {
        state.name.isNotBlank() || state.keywordsRaw.isNotBlank()
    }

    if (showKeywordDialog) {
        AlertDialog(
            onDismissRequest = { showKeywordDialog = false },
            title = {
                Text(
                    if (keywordDialogIndex == null) stringResource(R.string.rule_keyword_add)
                    else stringResource(R.string.rule_keyword_edit)
                )
            },
            text = {
                OutlinedTextField(
                    value = keywordDraft,
                    onValueChange = { keywordDraft = it },
                    label = { Text(stringResource(R.string.rule_keyword_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val saved = keywordDialogIndex?.let { viewModel.editKeyword(it, keywordDraft) }
                        ?: viewModel.addKeyword(keywordDraft)
                    if (saved) showKeywordDialog = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                Row {
                    keywordDialogIndex?.let { index ->
                        TextButton(onClick = {
                            viewModel.deleteKeyword(index)
                            showKeywordDialog = false
                        }) {
                            Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showKeywordDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        )
    }

    BackHandler {
        if (isModified) showUnsavedDialog = true else onBack()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.dialog_unsaved_title)) },
            text = { Text(stringResource(R.string.dialog_unsaved_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveRule {
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
                    Text(
                        if (state.isNew) stringResource(R.string.btn_add) else stringResource(R.string.btn_edit),
                        fontWeight = FontWeight.Bold
                    )
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
                        onClick = { viewModel.saveRule(onBack) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
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
            // Rule Name
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.rule_name)) },
                placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Source App
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSelectSourceApp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📷", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.rule_source_app), style = MaterialTheme.typography.labelMedium)
                        Text(
                            state.sourceLabel.ifBlank { stringResource(R.string.rule_source_app_empty_hint) },
                            fontWeight = FontWeight.Bold
                        )
                        if (state.sourcePackage.isNotBlank()) {
                            Text(
                                state.sourcePackage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("›", fontSize = 28.sp)
                }
            }

            // Match Mode Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.rule_match_mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.matchMode == MatchMode.CONTAINS_ANY,
                        onClick = { viewModel.updateMatchMode(MatchMode.CONTAINS_ANY) },
                        label = { Text(stringResource(R.string.rule_match_any_chip)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.matchMode == MatchMode.CONTAINS_ALL,
                        onClick = { viewModel.updateMatchMode(MatchMode.CONTAINS_ALL) },
                        label = { Text(stringResource(R.string.rule_match_all_chip)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (state.matchMode == MatchMode.CONTAINS_ANY) stringResource(R.string.rule_match_any_desc)
                    else stringResource(R.string.rule_match_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Keyword cards keep the matcher unchanged while removing CSV editing from the UI.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rule_keywords), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    if (state.keywordItems.isEmpty()) {
                        Text(
                            stringResource(R.string.rule_no_normalized_keywords),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(state.keywordItems) { index, keyword ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        keywordDialogIndex = index
                                        keywordDraft = keyword
                                        showKeywordDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(keyword, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    keywordDialogIndex = null
                    keywordDraft = ""
                    showKeywordDialog = true
                }) {
                    Text(stringResource(R.string.rule_keyword_add))
                }
            }

            // Guidance & Real-world Example Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rule_example_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.rule_example_camera_msg),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.rule_example_keywords),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Real-time Normalized Keywords Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rule_normalized_keywords_title, state.normalizedKeywords.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.normalizedKeywords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.rule_no_normalized_keywords),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = state.normalizedKeywords.joinToString(separator = " • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Short keyword warning if any
            val shortKeyword = state.normalizedKeywords.firstOrNull { it.length < 2 }
            if (shortKeyword != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rule_warn_short_keyword, shortKeyword),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Priority
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
                    Text("${stringResource(R.string.rule_priority)}: ${state.priority}", fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.rule_priority_desc), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { viewModel.updatePriority(state.priority - 1) },
                        enabled = state.priority > 1
                    ) { Text("-") }
                    Button(
                        onClick = { viewModel.updatePriority(state.priority + 1) },
                        enabled = state.priority < RuleViewModel.MAX_RULES
                    ) { Text("+") }
                }
            }

            // Enabled Switch
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
                    Text(stringResource(R.string.rule_enabled), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.rule_enabled_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::updateEnabled
                )
            }

            // Error message if any
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Save Button
            Button(
                onClick = { viewModel.saveRule(onBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_save), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
