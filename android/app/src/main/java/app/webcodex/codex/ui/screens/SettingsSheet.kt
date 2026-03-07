package app.webcodex.codex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.webcodex.codex.ui.CodexViewModel
import app.webcodex.codex.ui.ModelOption
import app.webcodex.codex.ui.theme.CodexColors
import app.webcodex.codex.ui.theme.LocalCodexColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: CodexViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val s = uiState.settings
    val c = LocalCodexColors.current

    var model by remember(s.model) { mutableStateOf(s.model) }
    var effort by remember(s.effort) { mutableStateOf(s.effort) }
    var approval by remember(s.approvalPolicy) { mutableStateOf(s.approvalPolicy) }
    var sandbox by remember(s.sandbox) { mutableStateOf(s.sandbox) }

    var theme by remember(s.theme) { mutableStateOf(s.theme) }
    var font by remember(s.font) { mutableStateOf(s.font) }
    var fontSize by remember(s.fontSize) { mutableStateOf(s.fontSize) }
    var lineHeight by remember(s.lineHeight) { mutableStateOf(s.lineHeight) }
    var lineWidth by remember(s.lineWidth) { mutableStateOf(s.lineWidth.toFloat()) }
    var borderRadius by remember(s.borderRadius) { mutableStateOf(s.borderRadius.toFloat()) }

    val modelOptions = remember(uiState.models, model) {
        buildList {
            add(ModelOption("", "Default"))
            addAll(uiState.models)
            if (model.isNotEmpty() && uiState.models.none { it.value == model }) {
                add(1, ModelOption(model, "$model (Current)"))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))

            // ─── AI & Connection ─────────────────────────────────────────────
            SectionHeader("AI & Connection")
            Spacer(Modifier.height(10.dp))

            SettingsDropdown(
                label = "Model",
                value = model,
                options = modelOptions.map { it.value to it.label },
                supportingText = "Applied on next turn"
            ) {
                model = it; viewModel.updateSettings { s -> s.copy(model = it) }
            }
            Spacer(Modifier.height(12.dp))
            SettingsDropdown(
                label = "Reasoning Effort",
                value = effort,
                options = listOf(
                    "" to "Default",
                    "low" to "Low – faster",
                    "medium" to "Medium",
                    "high" to "High – thorough"
                )
            ) {
                effort = it; viewModel.updateSettings { s -> s.copy(effort = it) }
            }
            Spacer(Modifier.height(12.dp))
            SettingsDropdown(
                label = "Approval Policy",
                value = approval,
                options = listOf(
                    "on-request" to "On Request",
                    "untrusted" to "Unless Trusted",
                    "never" to "Full Auto"
                ),
                supportingText = "Controls when Cortex asks for approval"
            ) {
                approval = it; viewModel.updateSettings { s -> s.copy(approvalPolicy = it) }
            }
            Spacer(Modifier.height(12.dp))
            SettingsDropdown(
                label = "Sandbox",
                value = sandbox,
                options = listOf(
                    "workspaceWrite" to "Workspace Write",
                    "readOnly" to "Read Only",
                    "dangerFullAccess" to "Danger Full Access"
                ),
                supportingText = "File access permissions for the agent"
            ) {
                sandbox = it; viewModel.updateSettings { s -> s.copy(sandbox = it) }
            }

            // ─── Theme ───────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(20.dp))
            SectionHeader("Theme")
            Spacer(Modifier.height(10.dp))

            SettingsDropdown(
                label = "Theme",
                value = theme,
                options = listOf(
                    "default" to "Default",
                    "amoled" to "AMOLED",
                    "tokyo-night" to "Tokyo Night Storm",
                    "catppuccin-mocha" to "Catppuccin Mocha",
                    "catppuccin-latte" to "Catppuccin Latte",
                    "dracula" to "Dracula",
                    "nord" to "Nord",
                    "gruvbox" to "Gruvbox Dark",
                    "light" to "Light"
                )
            ) {
                theme = it; viewModel.updateSettings { s -> s.copy(theme = it) }
            }

            // Theme colour swatches (read-only preview row)
            Spacer(Modifier.height(10.dp))
            ThemeSwatches(c)

            // ─── Font ────────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(20.dp))
            SectionHeader("Font")
            Spacer(Modifier.height(10.dp))

            SettingsDropdown(
                label = "Font",
                value = font,
                options = listOf(
                    "current" to "Current (Manrope + JetBrains Mono)",
                    "cursor" to "Cursor (Segoe UI + Cascadia)",
                    "anthropic" to "Anthropic (Source Sans 3)",
                    "fira" to "Fira Code",
                    "ibm" to "IBM Plex",
                    "dm" to "DM Sans"
                )
            ) {
                font = it; viewModel.updateSettings { s -> s.copy(font = it) }
            }

            // ─── Typography ──────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(20.dp))
            SectionHeader("Typography")
            Spacer(Modifier.height(10.dp))

            SliderRow(
                label = "Font size",
                value = fontSize,
                valueLabel = "${fontSize.toInt()}",
                min = 11f, max = 30f, steps = 19,
                onChange = { fontSize = it },
                onChangeFinished = { viewModel.updateSettings { s -> s.copy(fontSize = fontSize) } }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Release to apply",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                label = "Line height",
                value = lineHeight,
                valueLabel = String.format("%.1f", lineHeight),
                min = 1.0f, max = 2.5f, steps = 29,
                onChange = { lineHeight = it },
                onChangeFinished = { viewModel.updateSettings { s -> s.copy(lineHeight = lineHeight) } }
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                label = "Line width",
                value = lineWidth,
                valueLabel = "${lineWidth.toInt()}",
                min = 400f, max = 1200f, steps = 19,
                onChange = { lineWidth = it },
                onChangeFinished = { viewModel.updateSettings { s -> s.copy(lineWidth = lineWidth.toInt()) } }
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                label = "Border radius",
                value = borderRadius,
                valueLabel = "${borderRadius.toInt()}",
                min = 0f, max = 24f, steps = 24,
                onChange = { borderRadius = it },
                onChangeFinished = { viewModel.updateSettings { s -> s.copy(borderRadius = borderRadius.toInt()) } }
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ThemeSwatches(c: CodexColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(c.bg, c.surface, c.surface2, c.border, c.text2, c.accent).forEach { col ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(col)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    min: Float,
    max: Float,
    steps: Int,
    onChange: (Float) -> Unit,
    onChangeFinished: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = min..max,
            steps = steps,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp).padding(start = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    supportingText: String? = null,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second ?: value.ifEmpty { "Default" }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
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
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optValue, optLabel) ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(optLabel, modifier = Modifier.weight(1f))
                                if (optValue == value) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelected(optValue)
                        }
                    )
                }
            }
        }
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
