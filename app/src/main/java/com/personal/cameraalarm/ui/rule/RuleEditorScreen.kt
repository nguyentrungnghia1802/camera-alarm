package com.personal.cameraalarm.ui.rule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.trigger.MatchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    viewModel: RuleViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.editorState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id != null) "Edit Rule" else "New Rule") },
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
            // Rule Name
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Rule Name") },
                placeholder = { Text("e.g. Person Detected") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Source App
            OutlinedTextField(
                value = state.sourcePackage,
                onValueChange = { },
                label = { Text("Source Package") },
                readOnly = true,
                supportingText = {
                    Text(
                        if (state.sourcePackage.isBlank())
                            "No camera source selected. Go to Dashboard -> Source App to select one."
                        else "Inherited from active Camera Source app."
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Match Mode
            Text("Match Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.matchMode == MatchMode.CONTAINS_ANY,
                    onClick = { viewModel.updateMatchMode(MatchMode.CONTAINS_ANY) },
                    label = { Text("CONTAINS_ANY (any keyword)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.matchMode == MatchMode.CONTAINS_ALL,
                    onClick = { viewModel.updateMatchMode(MatchMode.CONTAINS_ALL) },
                    label = { Text("CONTAINS_ALL (all keywords)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Keywords Editor
            OutlinedTextField(
                value = state.keywordsRaw,
                onValueChange = viewModel::updateKeywordsRaw,
                label = { Text("Keywords (one per line)") },
                placeholder = { Text("human detected\nperson\nmotion") },
                minLines = 4,
                maxLines = 8,
                supportingText = {
                    Text("Enter 1 to 30 keywords. Each keyword max 100 characters.")
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Real-time Normalized Keywords Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Normalized Preview (${state.normalizedKeywords.size} keywords):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.normalizedKeywords.isEmpty()) {
                        Text(
                            text = "No valid keywords entered yet.",
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

            // Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Priority: ${state.priority}", fontWeight = FontWeight.Bold)
                    Text("Lower number = evaluated first", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { viewModel.updatePriority(state.priority - 1) },
                        enabled = state.priority > 1
                    ) { Text("-") }
                    Button(
                        onClick = { viewModel.updatePriority(state.priority + 1) },
                        enabled = state.priority < 100
                    ) { Text("+") }
                }
            }

            // Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enable this rule", fontWeight = FontWeight.Bold)
                    Text("Active rules will trigger alarm upon matching notification", style = MaterialTheme.typography.bodySmall)
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
                    .height(52.dp)
            ) {
                Text("Save Rule", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
