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
    onBack: () -> Unit
) {
    val state by viewModel.editorState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id != null) stringResource(R.string.dialog_edit_time_range) else stringResource(R.string.btn_add), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_cancel))
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
                placeholder = { Text("Ví dụ: Phát hiện người") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Source App
            OutlinedTextField(
                value = state.sourcePackage,
                onValueChange = { },
                label = { Text(stringResource(R.string.setup_source_app)) },
                readOnly = true,
                supportingText = {
                    Text(
                        if (state.sourcePackage.isBlank()) "Chưa chọn ứng dụng camera. Vào Bảo vệ -> Ứng dụng camera để chọn."
                        else "Kế thừa từ ứng dụng camera đang được chọn."
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Match Mode
            Text(stringResource(R.string.rule_match_mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.matchMode == MatchMode.CONTAINS_ANY,
                    onClick = { viewModel.updateMatchMode(MatchMode.CONTAINS_ANY) },
                    label = { Text(stringResource(R.string.rule_match_any)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.matchMode == MatchMode.CONTAINS_ALL,
                    onClick = { viewModel.updateMatchMode(MatchMode.CONTAINS_ALL) },
                    label = { Text(stringResource(R.string.rule_match_all)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Keywords Editor
            OutlinedTextField(
                value = state.keywordsRaw,
                onValueChange = viewModel::updateKeywordsRaw,
                label = { Text(stringResource(R.string.rule_keywords)) },
                placeholder = { Text(stringResource(R.string.rule_keywords_hint)) },
                minLines = 4,
                maxLines = 8,
                supportingText = {
                    Text("Nhập từ 1 đến 30 từ khóa (mỗi dòng một từ hoặc cách nhau bằng dấu phẩy).")
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
                        text = "Từ khóa nhận diện (${state.normalizedKeywords.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.normalizedKeywords.isEmpty()) {
                        Text(
                            text = "Chưa có từ khóa hợp lệ nào.",
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
                    Text(stringResource(R.string.rule_enabled), fontWeight = FontWeight.Bold)
                    Text("Quy tắc bật sẽ phát chuông khi thông báo khớp", style = MaterialTheme.typography.bodySmall)
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
                Text(stringResource(R.string.btn_save), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
