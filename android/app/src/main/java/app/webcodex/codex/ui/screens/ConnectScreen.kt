package app.webcodex.codex.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.webcodex.codex.network.WorkspaceTreeNode
import app.webcodex.codex.storage.TokenStore
import app.webcodex.codex.ui.CodexViewModel
import app.webcodex.codex.ui.theme.LocalCodexColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    modifier: Modifier = Modifier,
    viewModel: CodexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    val c = LocalCodexColors.current

    var host by remember { mutableStateOf(uiState.serverHost) }
    var port by remember { mutableStateOf(uiState.serverPort) }
    var token by remember { mutableStateOf(uiState.token.ifEmpty { tokenStore.token ?: "" }) }
    var rememberToken by remember { mutableStateOf(tokenStore.rememberToken) }
    var sessionModel by remember(uiState.settings.model) { mutableStateOf(uiState.settings.model) }
    var sessionApproval by remember(uiState.settings.approvalPolicy) { mutableStateOf(uiState.settings.approvalPolicy) }
    var sessionSandbox by remember(uiState.settings.sandbox) { mutableStateOf(uiState.settings.sandbox) }
    var authVerified by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedWsPath by remember { mutableStateOf(uiState.workspacePath ?: "") }
    var showSearchResults by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    // Breadcrumb for flat directory navigation: list of (name, path) pairs
    var breadcrumb by remember { mutableStateOf(listOf("" to "Workspaces")) }

    val currentStage = uiState.connectStage
    // Children at the current breadcrumb level
    val currentDirNodes = remember(breadcrumb, uiState.workspaceTree) {
        val currentPath = breadcrumb.last().second
        if (currentPath.isEmpty()) uiState.workspaceTree
        else findNodeChildren(uiState.workspaceTree, currentPath)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Stage indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .size(if (currentStage >= i) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                currentStage > i -> c.green
                                currentStage == i -> c.accent
                                else -> c.border
                            }
                        )
                )
                if (i < 3) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (currentStage > i) c.green.copy(alpha = 0.5f) else c.border.copy(alpha = 0.3f))
                    )
                }
            }
        }

        Text(
            "Cortex",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            when (currentStage) {
                1 -> "Connect to your server"
                2 -> "Pick a workspace"
                3 -> "Session settings"
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // ── Stage 1: Auth (simplified) ──────────────────────
        AnimatedVisibility(visible = currentStage == 1, enter = fadeIn(), exit = fadeOut()) {
            Column {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; viewModel.setToken(it); authVerified = false },
                    label = { Text("Auth Token") },
                    placeholder = { Text("Enter token…") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = fieldColors
                )

                Button(
                    onClick = {
                        authVerified = false
                        viewModel.loadWorkspaceTree(token, host, port)
                    },
                    enabled = token.isNotBlank() && !uiState.workspaceTreeLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 8.dp)
                ) {
                    if (uiState.workspaceTreeLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Continue")
                }

                // Remember token
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberToken,
                        onCheckedChange = { rememberToken = it; tokenStore.rememberToken = it }
                    )
                    Text("Remember token", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Advanced toggle
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Hide advanced" else "Advanced", style = MaterialTheme.typography.bodySmall)
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it; viewModel.setServerHost(it); authVerified = false },
                                label = { Text("Server Host") },
                                placeholder = { Text("10.0.2.2") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                                colors = fieldColors
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it; viewModel.setServerPort(it); authVerified = false },
                                label = { Text("Port") },
                                placeholder = { Text("3000") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = fieldColors
                            )
                        }
                    }
                }

                // Error
                uiState.workspacesError?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp, 14.dp))
                    }
                }
            }
        }

        // ── Stage 2: Workspace Browser (flat directory navigation) ──────
        AnimatedVisibility(visible = currentStage == 2, enter = fadeIn(), exit = fadeOut()) {
            Column {
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        showSearchResults = it.isNotEmpty()
                        if (it.isNotEmpty()) {
                            viewModel.searchWorkspaces(token, it, host, port)
                        }
                    },
                    label = { Text("Search workspaces") },
                    placeholder = { Text("Type to filter…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    singleLine = true,
                    colors = fieldColors
                )

                if (showSearchResults && uiState.workspaceSearchResults.isNotEmpty()) {
                    // Search results (flat list, same as before)
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                            items(uiState.workspaceSearchResults) { node ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedWsPath = node.path
                                            viewModel.setWorkspacePath(node.path)
                                            showSearchResults = false
                                        }
                                        .background(
                                            if (node.path == selectedWsPath || node.path == uiState.workspacePath)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(node.display.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text(node.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (node.path == selectedWsPath || node.path == uiState.workspacePath) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                } else if (!showSearchResults) {
                    // Flat directory browser — breadcrumb navigation
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        if (uiState.workspaceTree.isEmpty() && uiState.workspaceTreeLoading) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (uiState.workspaceTree.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No workspaces found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column {
                                // Breadcrumb trail
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    breadcrumb.forEachIndexed { idx, (name, _) ->
                                        if (idx > 0) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            name.ifEmpty { "Workspaces" },
                                            color = if (idx == breadcrumb.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.clickable(enabled = idx < breadcrumb.lastIndex) {
                                                breadcrumb = breadcrumb.take(idx + 1)
                                            }
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                                    // ".." entry when not at root
                                    if (breadcrumb.size > 1) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { breadcrumb = breadcrumb.dropLast(1) }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("←", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("..", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    items(currentDirNodes) { node ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (node.children.isNotEmpty()) {
                                                        breadcrumb = breadcrumb + (node.name to node.path)
                                                    } else {
                                                        selectedWsPath = node.path
                                                        viewModel.setWorkspacePath(node.path)
                                                    }
                                                }
                                                .background(
                                                    if (node.path == selectedWsPath || node.path == uiState.workspacePath)
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                    else androidx.compose.ui.graphics.Color.Transparent
                                                )
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (node.children.isNotEmpty()) c.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(node.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                                Text(node.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                            if (node.path == selectedWsPath || node.path == uiState.workspacePath) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { viewModel.goToPrevConnectStage() }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                    Button(
                        onClick = { viewModel.goToNextConnectStage() },
                        enabled = selectedWsPath.isNotBlank()
                    ) {
                        Text("Continue")
                    }
                }

                uiState.workspacesError?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp, 14.dp))
                    }
                }
            }
        }

        // ── Stage 3: Session Settings + Connect ────────────
        AnimatedVisibility(visible = currentStage == 3, enter = fadeIn(), exit = fadeOut()) {
            Column {
                // Selected workspace display
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                (selectedWsPath.ifEmpty { uiState.workspacePath ?: "" }).substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                selectedWsPath.ifEmpty { uiState.workspacePath ?: "" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Model
                val modelOptions = listOf("" to "Default") + uiState.models.map { it.value to it.label }
                var modelExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = modelOptions.firstOrNull { it.first == sessionModel }?.second ?: "Default",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        modelOptions.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateSettings { it.copy(model = value) }; sessionModel = value; modelExpanded = false })
                        }
                    }
                }

                // Approval
                var approvalExpanded by remember { mutableStateOf(false) }
                val approvalOptions = listOf("on-request" to "On Request", "untrusted" to "Unless Trusted", "never" to "Full Auto")
                ExposedDropdownMenuBox(expanded = approvalExpanded, onExpandedChange = { approvalExpanded = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = approvalOptions.firstOrNull { it.first == sessionApproval }?.second ?: sessionApproval,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Approval Policy") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = approvalExpanded) },
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(expanded = approvalExpanded, onDismissRequest = { approvalExpanded = false }) {
                        approvalOptions.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateSettings { it.copy(approvalPolicy = value) }; sessionApproval = value; approvalExpanded = false })
                        }
                    }
                }

                // Sandbox
                var sandboxExpanded by remember { mutableStateOf(false) }
                val sandboxOptions = listOf("workspaceWrite" to "Workspace Write", "readOnly" to "Read Only", "dangerFullAccess" to "Danger Full Access")
                ExposedDropdownMenuBox(expanded = sandboxExpanded, onExpandedChange = { sandboxExpanded = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = sandboxOptions.firstOrNull { it.first == sessionSandbox }?.second ?: sessionSandbox,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sandbox") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sandboxExpanded) },
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(expanded = sandboxExpanded, onDismissRequest = { sandboxExpanded = false }) {
                        sandboxOptions.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateSettings { it.copy(sandbox = value) }; sessionSandbox = value; sandboxExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { viewModel.goToPrevConnectStage() }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            viewModel.connect(host, port, token, selectedWsPath.ifBlank { uiState.workspacePath?.takeIf { it.isNotBlank() } }, rememberToken)
                        },
                        modifier = Modifier.height(48.dp),
                        enabled = uiState.connectionStatus != "connecting"
                    ) {
                        Text(if (uiState.connectionStatus == "connecting") "Connecting…" else "Connect")
                    }
                }

                // Offline cache button
                if (uiState.hasOfflineCache) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.resumeOfflineCache() },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Open Offline Cache")
                    }
                }

                if (uiState.error != null && uiState.workspacesError == null) {
                    uiState.error?.let { err ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                        ) {
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp, 14.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun findNodeChildren(nodes: List<WorkspaceTreeNode>, targetPath: String): List<WorkspaceTreeNode> {
    for (n in nodes) {
        if (n.path == targetPath) return n.children
        val found = findNodeChildren(n.children, targetPath)
        if (found.isNotEmpty()) return found
    }
    return emptyList()
}