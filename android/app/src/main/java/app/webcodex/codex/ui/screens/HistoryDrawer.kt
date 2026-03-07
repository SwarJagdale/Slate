package app.webcodex.codex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.webcodex.codex.ui.ActiveSession
import app.webcodex.codex.ui.CodexViewModel
import app.webcodex.codex.ui.theme.LocalCodexColors

// ═══════════════════════════════════════════════════════════════
// Reusable status dot composable
// ═══════════════════════════════════════════════════════════════

@Composable
fun SessionStatusDot(
    session: ActiveSession?,
    modifier: Modifier = Modifier,
    size: Int = 8
) {
    if (session == null) return
    val c = LocalCodexColors.current
    val color = if (session.turnRunning) c.yellow else c.green
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ═══════════════════════════════════════════════════════════════
// History drawer sheet content
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDrawerContent(
    viewModel: CodexViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeSessionsMap = viewModel.activeSessions
    val c = LocalCodexColors.current
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

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(c.surface)
            .padding(top = 48.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chats", style = MaterialTheme.typography.titleMedium, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { viewModel.openNewChatModal() }) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = c.accent)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Close", tint = c.text2)
                }
            }
        }

        // Search
        OutlinedTextField(
            value = uiState.historySearchQuery,
            onValueChange = { viewModel.setHistorySearchQuery(it) },
            placeholder = { Text("Search…", color = c.muted) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent,
                unfocusedBorderColor = c.border,
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
                cursorColor = c.accent,
                focusedContainerColor = c.surface2,
                unfocusedContainerColor = c.surface2
            )
        )
        Spacer(Modifier.height(8.dp))

        // Workspace filter
        var wsExpanded by remember { mutableStateOf(false) }
        val wsOptions = listOf("" to "All workspaces") + uiState.workspaces.map { it.path to it.name }
        ExposedDropdownMenuBox(
            expanded = wsExpanded,
            onExpandedChange = { wsExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = wsOptions.firstOrNull { it.first == (uiState.historyWorkspaceFilter ?: "") }?.second ?: "All workspaces",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wsExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.border,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text
                )
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
        Spacer(Modifier.height(4.dp))

        // Include archived
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.historyIncludeArchived,
                onCheckedChange = { viewModel.setHistoryIncludeArchived(it) },
                colors = CheckboxDefaults.colors(checkedColor = c.accent)
            )
            Text("Include archived", style = MaterialTheme.typography.bodyMedium, color = c.text2)
        }

        Spacer(Modifier.height(4.dp))

        // Thread list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(filteredThreads) { t ->
                val isActive = t.id == uiState.threadId
                val session = activeSessionsMap[t.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.resumeThread(t.id)
                            onDismiss()
                        }
                        .padding(0.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active thread rail highlight
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 40.dp)
                            .background(
                                if (isActive) c.accent
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Status dot
                            SessionStatusDot(session = session)
                            Text(
                                t.preview.take(60) + if (t.preview.length > 60) "…" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) c.accent else c.text,
                                maxLines = 1
                            )
                        }
                        Text(
                            java.text.SimpleDateFormat("MMM d, h:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(t.updatedAt * 1000)),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Legacy wrapper — keeps existing callers working during migration
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDrawer(
    viewModel: CodexViewModel,
    onDismiss: () -> Unit
) {
    HistoryDrawerContent(viewModel = viewModel, onDismiss = onDismiss)
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
