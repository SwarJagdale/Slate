package app.webcodex.codex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.webcodex.codex.storage.TokenStore
import app.webcodex.codex.ui.CodexViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    modifier: Modifier = Modifier,
    viewModel: CodexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }

    var host by remember { mutableStateOf(uiState.serverHost) }
    var port by remember { mutableStateOf(uiState.serverPort) }
    var token by remember { mutableStateOf(uiState.token.ifEmpty { tokenStore.token ?: "" }) }
    var workspace by remember { mutableStateOf(uiState.workspacePath ?: "") }
    var rememberToken by remember { mutableStateOf(tokenStore.rememberToken) }
    var sessionModel by remember(uiState.settings.model) { mutableStateOf(uiState.settings.model) }
    var sessionApproval by remember(uiState.settings.approvalPolicy) { mutableStateOf(uiState.settings.approvalPolicy) }
    var sessionSandbox by remember(uiState.settings.sandbox) { mutableStateOf(uiState.settings.sandbox) }
    var showSessionSettings by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.serverHost, uiState.serverPort) {
        host = uiState.serverHost
        port = uiState.serverPort
    }
    LaunchedEffect(uiState.workspaces) {
        if (uiState.workspaces.isNotEmpty() && workspace.isEmpty()) {
            workspace = uiState.workspaces.first().path
        }
    }
    LaunchedEffect(token, host, port) {
        if (token.isNotBlank()) viewModel.loadWorkspaces(token, host, port)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Cortex",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Connect to your workspace",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it; viewModel.setToken(it) },
            label = { Text("Auth Token") },
            placeholder = { Text("Enter token…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        if (uiState.workspaces.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.workspaces.firstOrNull { it.path == workspace }?.name ?: workspace,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Workspace") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    uiState.workspaces.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.name) },
                            onClick = {
                                workspace = opt.path
                                viewModel.setWorkspacePath(opt.path)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { showSessionSettings = !showSessionSettings },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (showSessionSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Session Settings", style = MaterialTheme.typography.bodyMedium)
        }

        if (showSessionSettings) {
            val modelOptions = listOf("" to "Default") + uiState.models.map { it.value to it.label }
            var modelExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = modelOptions.firstOrNull { it.first == sessionModel }?.second ?: "Default",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) }
                )
                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    modelOptions.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateSettings { it.copy(model = value) }; sessionModel = value; modelExpanded = false })
                    }
                }
            }
            var approvalExpanded by remember { mutableStateOf(false) }
            val approvalOptions = listOf("on-request" to "On Request", "untrusted" to "Unless Trusted", "never" to "Full Auto (no approvals)")
            ExposedDropdownMenuBox(expanded = approvalExpanded, onExpandedChange = { approvalExpanded = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = approvalOptions.firstOrNull { it.first == sessionApproval }?.second ?: sessionApproval,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Approval Policy") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = approvalExpanded) }
                )
                ExposedDropdownMenu(expanded = approvalExpanded, onDismissRequest = { approvalExpanded = false }) {
                    approvalOptions.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateSettings { it.copy(approvalPolicy = value) }; sessionApproval = value; approvalExpanded = false })
                    }
                }
            }
            var sandboxExpanded by remember { mutableStateOf(false) }
            val sandboxOptions = listOf("workspaceWrite" to "Workspace Write", "readOnly" to "Read Only", "dangerFullAccess" to "Danger Full Access")
            ExposedDropdownMenuBox(expanded = sandboxExpanded, onExpandedChange = { sandboxExpanded = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = sandboxOptions.firstOrNull { it.first == sessionSandbox }?.second ?: sessionSandbox,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sandbox") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sandboxExpanded) }
                )
                ExposedDropdownMenu(expanded = sandboxExpanded, onDismissRequest = { sandboxExpanded = false }) {
                    sandboxOptions.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateSettings { it.copy(sandbox = value) }; sessionSandbox = value; sandboxExpanded = false })
                    }
                }
            }
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; viewModel.setServerHost(it) },
                label = { Text("Server host") },
                placeholder = { Text("10.0.2.2 or your server IP") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it; viewModel.setServerPort(it) },
                label = { Text("Port") },
                placeholder = { Text("3000") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberToken,
                onCheckedChange = { rememberToken = it; tokenStore.rememberToken = it }
            )
            Text("Remember token", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                viewModel.loadWorkspaces(token, host, port)
                viewModel.connect(host, port, token, workspace.ifBlank { null }, rememberToken)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = 8.dp)
        ) {
            Text(if (uiState.connectionStatus == "connecting") "Connecting…" else "Connect")
        }

        if (uiState.hasOfflineCache) {
            OutlinedButton(
                onClick = { viewModel.resumeOfflineCache() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 8.dp)
            ) {
                Text("Open Offline Cache")
            }
            Text(
                "Loads the last synced session in read-only mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        uiState.error?.let { err ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
            ) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp, 14.dp)
                )
            }
        }
    }
}
