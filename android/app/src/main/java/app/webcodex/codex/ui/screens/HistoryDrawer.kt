package app.webcodex.codex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.webcodex.codex.ui.CodexViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDrawer(
    viewModel: CodexViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredThreads = remember(uiState.threadList, uiState.historySearchQuery) {
        val q = uiState.historySearchQuery.trim().lowercase()
        if (q.isEmpty()) uiState.threadList
        else uiState.threadList.filter { it.preview.lowercase().contains(q) }
    }

    if (uiState.showNewChatModal) {
        NewChatModal(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissNewChatModal() },
            onStart = { workspacePath ->
                viewModel.startNewThread(workspacePath)
                viewModel.dismissNewChatModal()
                onDismiss()
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chats", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { viewModel.openNewChatModal() },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("New", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Collapse")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.historySearchQuery,
                onValueChange = { viewModel.setHistorySearchQuery(it) },
                placeholder = { Text("Search…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            var wsExpanded by remember { mutableStateOf(false) }
            val wsOptions = listOf("" to "All workspaces") + uiState.workspaces.map { it.path to it.name }
            ExposedDropdownMenuBox(
                expanded = wsExpanded,
                onExpandedChange = { wsExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = wsOptions.firstOrNull { it.first == (uiState.historyWorkspaceFilter ?: "") }?.second ?: "All workspaces",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wsExpanded) }
                )
                ExposedDropdownMenu(expanded = wsExpanded, onDismissRequest = { wsExpanded = false }) {
                    wsOptions.forEach { (path, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.setHistoryWorkspaceFilter(path.takeIf { it.isNotEmpty() })
                                wsExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.historyIncludeArchived,
                    onCheckedChange = { viewModel.setHistoryIncludeArchived(it) }
                )
                Text("Include archived", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(filteredThreads) { t ->
                    val isActive = t.id == uiState.threadId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.resumeThread(t.id)
                                onDismiss()
                            }
                            .padding(0.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .heightIn(min = 40.dp)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                t.preview.take(60) + if (t.preview.length > 60) "…" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                java.text.SimpleDateFormat("MMM d, h:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(t.updatedAt * 1000)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatModal(
    viewModel: CodexViewModel,
    onDismiss: () -> Unit,
    onStart: (workspacePath: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedWorkspace by remember(uiState.workspaces, uiState.workspacePath) {
        mutableStateOf(uiState.workspacePath ?: uiState.workspaces.firstOrNull()?.path ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chat") },
        text = {
            Column {
                if (uiState.workspaces.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        "Workspace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.workspaces.firstOrNull { it.path == selectedWorkspace }?.name
                                ?: selectedWorkspace.substringAfterLast('/').ifEmpty { "Default workspace" },
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            uiState.workspaces.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(opt.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                opt.path,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedWorkspace = opt.path
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "A new chat will be started in the current workspace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(selectedWorkspace.ifBlank { null }) }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
