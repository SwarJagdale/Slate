package app.webcodex.codex.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.webcodex.codex.ui.ActiveSession
import app.webcodex.codex.ui.CodexViewModel
import app.webcodex.codex.ui.theme.LocalCodexColors
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun ActiveSessionsOverlay(
    viewModel: CodexViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions = remember(viewModel.activeSessions.toMap()) {
        viewModel.sortedActiveSessions()
    }
    val c = LocalCodexColors.current

    // Animate the entire overlay in/out
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Backdrop — clicking ANYWHERE on this dismisses; two-finger swipe down also dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val pointers = down.changes.filter { it.pressed }
                        if (pointers.size < 2) continue
                        val startY = pointers.map { it.position.y }.average().toFloat()
                        val startX = pointers.map { it.position.x }.average().toFloat()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val cur = ev.changes.filter { it.pressed }
                            if (cur.size < 2) break
                            val dy = cur.map { it.position.y }.average().toFloat() - startY
                            val dx = cur.map { it.position.x }.average().toFloat() - startX
                            if (dy > 250f && abs(dy) > abs(dx) * 1.5f) {
                                cur.forEach { it.consume() }
                                onDismiss()
                                return@awaitPointerEventScope
                            }
                        }
                    }
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        // Animated scrim
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.bg.copy(alpha = 0.88f))
            )
        }

        // Content slides up with spring
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(180)
            ) + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 56.dp, bottom = 24.dp)
                    // DON'T fill height — let empty space below pass clicks to backdrop
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* block propagation on header */ },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Active Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.text
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = c.text2)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { /* block */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No active sessions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.muted
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp) // constrain height so empty space below is tappable
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { /* block grid area */ },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(sessions, key = { it.threadId }) { session ->
                            ActiveSessionCard(
                                session = session,
                                isCurrent = session.threadId == uiState.threadId,
                                c = c,
                                onClick = {
                                    viewModel.resumeThread(session.threadId)
                                    onDismiss()
                                },
                                onRemove = {
                                    viewModel.removeActiveSession(session.threadId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: ActiveSession,
    isCurrent: Boolean,
    c: app.webcodex.codex.ui.theme.CodexColors,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = if (isCurrent) 8f else 2f
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) c.accentDim else c.surface2,
        border = if (isCurrent) {
            androidx.compose.foundation.BorderStroke(1.5.dp, c.accent.copy(alpha = 0.7f))
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, c.border.copy(alpha = 0.5f))
        },
        tonalElevation = if (isCurrent) 4.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SessionStatusDot(session = session, size = 7)
                    Text(
                        if (session.turnRunning) "Running" else "Idle",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (session.turnRunning) c.yellow else c.green
                    )
                }
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = c.muted.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { onRemove() }
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                session.preview.ifEmpty { "New chat" },
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(3.dp))

            val wsLabel = session.workspaceName ?: session.workspacePath?.substringAfterLast('/') ?: ""
            if (wsLabel.isNotEmpty()) {
                Text(
                    wsLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val elapsed = remember(session.lastActivityAt) {
                val diff = System.currentTimeMillis() - session.lastActivityAt
                when {
                    diff < 60_000 -> "now"
                    diff < 3_600_000 -> "${diff / 60_000}m"
                    diff < 86_400_000 -> "${diff / 3_600_000}h"
                    else -> "${diff / 86_400_000}d"
                }
            }
            Text(elapsed, style = MaterialTheme.typography.labelSmall, color = c.muted.copy(alpha = 0.5f))
        }
    }
}
