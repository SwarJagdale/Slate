package app.webcodex.codex.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import app.webcodex.codex.R
import app.webcodex.codex.ui.ChatMessage
import app.webcodex.codex.ui.CodexViewModel
import app.webcodex.codex.ui.MessageType
import app.webcodex.codex.ui.theme.CodexTheme
import app.webcodex.codex.ui.theme.LocalCodexColors
import org.json.JSONObject
import org.commonmark.parser.Parser
import org.commonmark.node.*
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds

private const val URL_ANNOTATION_TAG = "url"

// ═══════════════════════════════════════════════════════════════
// Markdown rendering — commonmark AST → Compose
// ═══════════════════════════════════════════════════════════════

private val mdExtensions = listOf(
    StrikethroughExtension.create(),
    TablesExtension.create()
)
private val mdParser: Parser = Parser.builder().extensions(mdExtensions).build()

/** Sealed type for pre-parsed markdown blocks — cheap to hold in memory. */
private sealed class MdBlock {
    data class Paragraph(val inlines: List<MdInline>) : MdBlock()
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock()
    data class CodeBlock(val lang: String, val code: String) : MdBlock()
    data class BlockQuote(val children: List<MdBlock>) : MdBlock()
    data class ListBlock(val ordered: Boolean, val startNumber: Int, val items: List<List<MdBlock>>) : MdBlock()
    object ThematicBreak : MdBlock()
    data class Table(val header: List<List<MdInline>>, val rows: List<List<List<MdInline>>>) : MdBlock()
}

private sealed class MdInline {
    data class Text(val text: String) : MdInline()
    data class Bold(val children: List<MdInline>) : MdInline()
    data class Italic(val children: List<MdInline>) : MdInline()
    data class Code(val code: String) : MdInline()
    data class Link(val url: String, val children: List<MdInline>) : MdInline()
    data class Strike(val children: List<MdInline>) : MdInline()
    data class SoftBreak(val dummy: Unit = Unit) : MdInline()
    data class HardBreak(val dummy: Unit = Unit) : MdInline()
}

/** Parse markdown string → list of MdBlock. */
private fun parseMarkdown(source: String): List<MdBlock> {
    val doc = mdParser.parse(source)
    return collectBlocks(doc)
}

private fun collectBlocks(parent: Node): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var child = parent.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Paragraph -> blocks.add(MdBlock.Paragraph(collectInlines(child)))
            is org.commonmark.node.Heading -> blocks.add(MdBlock.Heading(child.level, collectInlines(child)))
            is FencedCodeBlock -> blocks.add(MdBlock.CodeBlock(child.info ?: "", child.literal?.trimEnd() ?: ""))
            is IndentedCodeBlock -> blocks.add(MdBlock.CodeBlock("", child.literal?.trimEnd() ?: ""))
            is org.commonmark.node.BlockQuote -> blocks.add(MdBlock.BlockQuote(collectBlocks(child)))
            is org.commonmark.node.ThematicBreak -> blocks.add(MdBlock.ThematicBreak)
            is BulletList -> {
                val items = mutableListOf<List<MdBlock>>()
                var li = child.firstChild
                while (li != null) { if (li is ListItem) items.add(collectBlocks(li)); li = li.next }
                blocks.add(MdBlock.ListBlock(ordered = false, startNumber = 0, items = items))
            }
            is OrderedList -> {
                val items = mutableListOf<List<MdBlock>>()
                var li = child.firstChild
                while (li != null) { if (li is ListItem) items.add(collectBlocks(li)); li = li.next }
                blocks.add(MdBlock.ListBlock(ordered = true, startNumber = child.startNumber, items = items))
            }
            is TableBlock -> {
                val header = mutableListOf<List<MdInline>>()
                val rows = mutableListOf<List<List<MdInline>>>()
                var section = child.firstChild
                while (section != null) {
                    val isHead = section is TableHead
                    var row = section.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            val cells = mutableListOf<List<MdInline>>()
                            var cell = row.firstChild
                            while (cell != null) { if (cell is TableCell) cells.add(collectInlines(cell)); cell = cell.next }
                            if (isHead) { header.clear(); header.addAll(cells) } else rows.add(cells)
                        }
                        row = row.next
                    }
                    section = section.next
                }
                blocks.add(MdBlock.Table(header, rows))
            }
            else -> {
                // Unknown block — try to extract text
                val sub = collectBlocks(child)
                if (sub.isNotEmpty()) blocks.addAll(sub)
            }
        }
        child = child.next
    }
    return blocks
}

private fun collectInlines(parent: Node): List<MdInline> {
    val inlines = mutableListOf<MdInline>()
    var child = parent.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Text -> inlines.add(MdInline.Text(child.literal))
            is org.commonmark.node.Code -> inlines.add(MdInline.Code(child.literal))
            is Emphasis -> inlines.add(MdInline.Italic(collectInlines(child)))
            is StrongEmphasis -> inlines.add(MdInline.Bold(collectInlines(child)))
            is org.commonmark.node.Link -> inlines.add(MdInline.Link(child.destination, collectInlines(child)))
            is Strikethrough -> inlines.add(MdInline.Strike(collectInlines(child)))
            is org.commonmark.node.SoftLineBreak -> inlines.add(MdInline.SoftBreak())
            is org.commonmark.node.HardLineBreak -> inlines.add(MdInline.HardBreak())
            else -> {
                // Image or other inline — just extract literal text
                val sub = collectInlines(child)
                if (sub.isNotEmpty()) inlines.addAll(sub) else {
                    val lit = (child as? org.commonmark.node.Text)?.literal
                    if (lit != null) inlines.add(MdInline.Text(lit))
                }
            }
        }
        child = child.next
    }
    return inlines
}

/** Build an AnnotatedString from a list of MdInline nodes. */
private fun buildAnnotatedInlines(
    inlines: List<MdInline>,
    linkColor: Color,
    codeBackground: Color,
    baseFontSize: Float = 14f,
): AnnotatedString = buildAnnotatedString {
    fun walk(nodes: List<MdInline>) {
        for (node in nodes) {
            when (node) {
                is MdInline.Text -> append(node.text)
                is MdInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = (baseFontSize - 1).sp)) { append(node.code) }
                is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { walk(node.children) }
                is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(node.children) }
                is MdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { walk(node.children) }
                is MdInline.Link -> {
                    pushStringAnnotation(URL_ANNOTATION_TAG, node.url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { walk(node.children) }
                    pop()
                }
                is MdInline.SoftBreak -> append(" ")
                is MdInline.HardBreak -> append("\n")
            }
        }
    }
    walk(inlines)
}

/** Top-level composable: renders parsed markdown blocks. */
@Composable
private fun MarkdownContent(
    blocks: List<MdBlock>,
    textColor: Color,
    linkColor: Color,
    codeBackground: Color,
    modifier: Modifier = Modifier,
    baseFontSize: Float = 14f,
) {
    val uriHandler = LocalUriHandler.current
    val c = LocalCodexColors.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            RenderBlock(block, textColor, linkColor, codeBackground, c, uriHandler, baseFontSize)
        }
    }
}

@Composable
private fun RenderBlock(
    block: MdBlock,
    textColor: Color,
    linkColor: Color,
    codeBackground: Color,
    c: app.webcodex.codex.ui.theme.CodexColors,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    baseFontSize: Float,
) {
    when (block) {
        is MdBlock.Paragraph -> {
            val annotated = remember(block) { buildAnnotatedInlines(block.inlines, linkColor, codeBackground, baseFontSize) }
            ClickableText(
                text = annotated,
                style = TextStyle(color = textColor, fontSize = baseFontSize.sp, lineHeight = (baseFontSize * 1.6f).sp),
                onClick = { offset -> annotated.getStringAnnotations(URL_ANNOTATION_TAG, offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) } }
            )
        }
        is MdBlock.Heading -> {
            val hSize = when (block.level) { 1 -> baseFontSize + 8; 2 -> baseFontSize + 5; 3 -> baseFontSize + 3; else -> baseFontSize + 1 }
            val annotated = remember(block) { buildAnnotatedInlines(block.inlines, linkColor, codeBackground, hSize) }
            ClickableText(
                text = annotated,
                style = TextStyle(color = textColor, fontSize = hSize.sp, fontWeight = FontWeight.Bold, lineHeight = (hSize * 1.4f).sp),
                modifier = Modifier.padding(top = 4.dp),
                onClick = { offset -> annotated.getStringAnnotations(URL_ANNOTATION_TAG, offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) } }
            )
        }
        is MdBlock.CodeBlock -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
            ) {
                if (block.lang.isNotEmpty()) {
                    Text(
                        block.lang,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.surface3)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.muted)
                    )
                }
                Text(
                    block.code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = textColor, lineHeight = 18.sp)
                )
            }
        }
        is MdBlock.BlockQuote -> {
            val accentColor = c.accent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = accentColor.copy(alpha = 0.5f),
                            start = Offset(2.dp.toPx(), 0f),
                            end = Offset(2.dp.toPx(), size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                    .padding(start = 14.dp)
            ) {
                MarkdownContent(block.children, c.text2, linkColor, codeBackground, baseFontSize = baseFontSize)
            }
        }
        is MdBlock.ListBlock -> {
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                block.items.forEachIndexed { idx, itemBlocks ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val bullet = if (block.ordered) "${block.startNumber + idx}." else "•"
                        Text(
                            bullet,
                            style = TextStyle(color = c.muted, fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(if (block.ordered) 24.dp else 16.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (b in itemBlocks) {
                                RenderBlock(b, textColor, linkColor, codeBackground, c, uriHandler, baseFontSize)
                            }
                        }
                    }
                }
            }
        }
        MdBlock.ThematicBreak -> {
            HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 4.dp))
        }
        is MdBlock.Table -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, c.border, RoundedCornerShape(6.dp))
            ) {
                // Header
                if (block.header.isNotEmpty()) {
                    Row(modifier = Modifier.background(c.surface2)) {
                        block.header.forEach { cellInlines ->
                            val annotated = buildAnnotatedInlines(cellInlines, linkColor, codeBackground, baseFontSize)
                            Text(
                                annotated,
                                modifier = Modifier.widthIn(min = 80.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                                style = TextStyle(color = textColor, fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    HorizontalDivider(color = c.border)
                }
                // Rows
                block.rows.forEachIndexed { rowIdx, row ->
                    Row(modifier = Modifier.background(if (rowIdx % 2 == 0) Color.Transparent else c.surface2.copy(alpha = 0.4f))) {
                        row.forEach { cellInlines ->
                            val annotated = buildAnnotatedInlines(cellInlines, linkColor, codeBackground, baseFontSize)
                            Text(
                                annotated,
                                modifier = Modifier.widthIn(min = 80.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                                style = TextStyle(color = textColor, fontSize = baseFontSize.sp)
                            )
                        }
                    }
                    if (rowIdx < block.rows.lastIndex) HorizontalDivider(color = c.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/**
 * Public entry point — replaces the old MarkdownRichText.
 * Parses markdown once via remember(content) and renders blocks.
 */
@Composable
private fun MarkdownRichText(
    content: String,
    baseStyle: TextStyle,
    textColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    applyPaddingAndBackground: Boolean = true
) {
    val c = LocalCodexColors.current
    val blocks = remember(content) { parseMarkdown(content) }

    val mod = if (applyPaddingAndBackground) {
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
    } else modifier.fillMaxWidth()

    MarkdownContent(
        blocks = blocks,
        textColor = textColor,
        linkColor = linkColor,
        codeBackground = c.surface3.copy(alpha = 0.6f),
        modifier = mod,
        baseFontSize = baseStyle.fontSize.value.takeIf { it > 0 } ?: 14f,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: CodexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val spacing = CodexTheme.spacing
    val radius = CodexTheme.radius
    val c = LocalCodexColors.current
    val isOfflineReadOnly = !uiState.isConnected
    var input by remember { mutableStateOf("") }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Drawer state — driven by uiState.showHistory
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(uiState.showHistory) {
        if (uiState.showHistory) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed && uiState.showHistory) {
            viewModel.setShowHistory(false)
        } else if (drawerState.currentValue == DrawerValue.Open && !uiState.showHistory) {
            viewModel.setShowHistory(true)
        }
    }

    // Session switch toast state (custom, replaces default Snackbar)
    var switchToastMessage by remember { mutableStateOf<String?>(null) }
    var toastDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    // Two-finger gesture state
    val density = LocalDensity.current
    val twoFingerThresholdHPx = with(density) { 80.dp.toPx() }
    val twoFingerThresholdVPx = with(density) { 35.dp.toPx() }

    // Interactive gesture-tracked swipe state
    val swipeOffset = remember { Animatable(0f) } // in px
    var peekTargetId by remember { mutableStateOf<String?>(null) }
    var peekDirection by remember { mutableStateOf(0) } // -1 swipe right (prev), 1 swipe left (next)
    val screenWidthPx = with(density) { (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp).dp.toPx() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                HistoryDrawerContent(
                    viewModel = viewModel,
                    onDismiss = { viewModel.setShowHistory(false) }
                )
            }
        },
        gesturesEnabled = !uiState.showSettings && !uiState.showActiveSessionsOverlay
    ) {

    // Two-finger gesture modifier
    val twoFingerGestureModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val firstDown = awaitPointerEvent()
                val pointers = firstDown.changes.filter { it.pressed }
                if (pointers.size < 2) continue

                val startAvgX = pointers.map { it.position.x }.average().toFloat()
                val startAvgY = pointers.map { it.position.y }.average().toFloat()
                var gestureAxis: String? = null // null = undecided, "h" = horizontal, "v" = vertical
                var committedDir = 0

                while (true) {
                    val event = awaitPointerEvent()
                    val current = event.changes.filter { it.pressed }
                    if (current.size < 2) {
                        // Fingers lifted — resolve gesture
                        if (gestureAxis == "h" && committedDir != 0) {
                            val offset = swipeOffset.value
                            val commitThreshold = screenWidthPx * 0.35f
                            if (abs(offset) > commitThreshold) {
                                // Commit switch
                                val targetId = peekTargetId
                                scope.launch {
                                    swipeOffset.animateTo(
                                        -committedDir * screenWidthPx,
                                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                    if (targetId != null) {
                                        viewModel.resumeThread(targetId)
                                        switchToastMessage = viewModel.activeSessions[targetId]?.preview?.take(30) ?: "Session"
                                        toastDismissJob?.cancel()
                                        toastDismissJob = scope.launch { delay(1500); switchToastMessage = null }
                                    }
                                    peekTargetId = null
                                    peekDirection = 0
                                    swipeOffset.snapTo(0f)
                                }
                            } else {
                                // Bounce back
                                scope.launch {
                                    swipeOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
                                    peekTargetId = null
                                    peekDirection = 0
                                }
                            }
                        }
                        break
                    }

                    val curAvgX = current.map { it.position.x }.average().toFloat()
                    val curAvgY = current.map { it.position.y }.average().toFloat()
                    val dx = curAvgX - startAvgX
                    val dy = curAvgY - startAvgY

                    // Decide gesture axis once past dead zone
                    if (gestureAxis == null && (abs(dx) > 30f || abs(dy) > 30f)) {
                        gestureAxis = if (abs(dx) > abs(dy)) "h" else "v"
                        if (gestureAxis == "v" && dy < -twoFingerThresholdVPx && abs(dy) > abs(dx) * 1.5f) {
                            viewModel.setShowActiveSessionsOverlay(true)
                            current.forEach { it.consume() }
                            break
                        }
                        if (gestureAxis == "h") {
                            // Determine direction and peek target
                            val forward = dx < 0
                            committedDir = if (forward) 1 else -1
                            peekDirection = committedDir
                            peekTargetId = viewModel.cycleActiveSession(forward)
                        }
                    }

                    // Track finger on horizontal axis
                    if (gestureAxis == "h" && committedDir != 0 && peekTargetId != null) {
                        val clampedDx = if (committedDir > 0) dx.coerceAtMost(0f) else dx.coerceAtLeast(0f)
                        scope.launch { swipeOffset.snapTo(clampedDx) }
                        current.forEach { it.consume() }
                    }
                    // Vertical gesture (not drag)
                    else if (gestureAxis == "v") {
                        if (dy < -twoFingerThresholdVPx && abs(dy) > abs(dx) * 1.5f) {
                            viewModel.setShowActiveSessionsOverlay(true)
                            current.forEach { it.consume() }
                            break
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

    Column(modifier = modifier.background(c.bg).then(twoFingerGestureModifier)) {

        // ─── Header ───────────────────────────────────────────────────────────
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_codex_brand),
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            "Cortex",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.accent
                        )
                    }

                    // Model picker (pill)
                    if (uiState.isConnected && uiState.models.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        var modelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .menuAnchor()
                                    .clickable { modelExpanded = true },
                                shape = RoundedCornerShape(20.dp),
                                color = c.surface2,
                                border = BorderStroke(1.dp, c.border)
                            ) {
                                Text(
                                    uiState.models.firstOrNull { it.value == uiState.settings.model }?.label
                                        ?: uiState.settings.model.ifEmpty { "Default" },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.text2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default") },
                                    onClick = {
                                        viewModel.updateSettings { it.copy(model = "") }
                                        modelExpanded = false
                                    }
                                )
                                uiState.models.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.label) },
                                        onClick = {
                                            viewModel.updateSettings { it.copy(model = opt.value) }
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Status pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surface2)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (uiState.connectionStatus) {
                                        "ready" -> c.green
                                        "error", "disconnected" -> c.red
                                        "working" -> c.yellow
                                        else -> c.muted
                                    }
                                )
                        )
                        Text(
                            uiState.connectionStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.text2
                        )
                    }
                    Spacer(Modifier.width(4.dp))

                    IconButton(onClick = { viewModel.toggleHistory() }) {
                        Icon(Icons.Default.Chat, contentDescription = "History", tint = c.text2)
                    }
                    IconButton(onClick = { viewModel.toggleSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = c.text2)
                    }
                    IconButton(onClick = {
                        if (uiState.isConnected) viewModel.disconnect() else viewModel.openConnectScreen()
                    }) {
                        Icon(
                            if (uiState.isConnected) Icons.Default.PowerOff else Icons.Default.Wifi,
                            contentDescription = if (uiState.isConnected) "Disconnect" else "Reconnect",
                            tint = c.muted
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = c.surface,
                titleContentColor = c.text
            )
        )

        if (isOfflineReadOnly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.yellowDim)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = c.yellow, modifier = Modifier.size(16.dp))
                Text(
                    "Offline cache only. You can read previous output, but sending is disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.text2
                )
            }
        }

        // ─── Queue strip ──────────────────────────────────────────────────────
        if (uiState.pendingQueue.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "QUEUED (${uiState.pendingQueue.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.muted
                    )
                    Text(
                        "Clear all",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.red,
                        modifier = Modifier.clickable { viewModel.clearQueue() }
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = true
                ) {
                    itemsIndexed(uiState.pendingQueue) { idx, text ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(radius.medium))
                                .background(c.surface2)
                                .border(1.dp, c.border, RoundedCornerShape(radius.medium))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.text2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = c.muted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.removeFromQueue(idx) }
                            )
                        }
                    }
                }
            }
        }

        // ─── Messages ─────────────────────────────────────────────────────────
        val lineWidthDp = CodexTheme.lineWidth
        val currentSwipeOffset = swipeOffset.value
        val peekMsgs = peekTargetId?.let { viewModel.sessionMessageCache[it] }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            // Current content — follows finger
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = currentSwipeOffset }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = lineWidthDp)
                        .align(Alignment.Center),
                    contentPadding = PaddingValues(horizontal = spacing.large, vertical = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    if (uiState.messages.isEmpty()) {
                        item { WelcomeSection(spacing, c) { input = it } }
                    }
                    items(uiState.messages) { msg ->
                        MessageRow(msg = msg, viewModel = viewModel, itemTexts = uiState.itemTexts)
                    }
                }
            }

            // Peek content — adjacent session sliding in from the edge
            if (peekTargetId != null && currentSwipeOffset != 0f) {
                val peekOffsetX = currentSwipeOffset + (peekDirection * screenWidthPx)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = peekOffsetX }
                ) {
                    val msgs = peekMsgs ?: emptyList()
                    val peekListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = (msgs.size - 1).coerceAtLeast(0)
                    )
                    LazyColumn(
                        state = peekListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = lineWidthDp)
                            .align(Alignment.Center),
                        contentPadding = PaddingValues(horizontal = spacing.large, vertical = spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        if (msgs.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Loading\u2026", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            items(msgs) { msg ->
                                MessageRow(msg = msg, viewModel = viewModel, itemTexts = emptyMap())
                            }
                        }
                    }
                }
            }
        }

        // ─── Input area ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = !isOfflineReadOnly,
                    placeholder = {
                        Text(
	                            if (isOfflineReadOnly) "Offline cache is read-only" else "Message Cortex… (/ for commands)",
                            color = c.muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 5,
                    shape = RoundedCornerShape(radius.medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.text,
                        unfocusedTextColor = c.text,
                        cursorColor = c.accent,
                        focusedContainerColor = c.surface2,
                        unfocusedContainerColor = c.surface2
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                // Send / Stop button
                if (uiState.activeTurnId != null) {
                    Button(
                        onClick = { viewModel.interrupt() },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.redDim,
                            contentColor = c.red
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(20.dp))
                    }
                } else {
                    Button(
                        onClick = { if (viewModel.sendMessage(input)) input = "" },
                        modifier = Modifier.size(44.dp),
                        enabled = !isOfflineReadOnly && input.isNotBlank(),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent,
                            contentColor = c.bg,
                            disabledContainerColor = c.surface2,
                            disabledContentColor = c.muted
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Hint + status bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isOfflineReadOnly) "Reconnect to continue chatting" else "Enter to send · Shift+Enter for new line",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.muted
                )
                // Token usage + rate limit bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.tokenUsage?.let { u ->
                        Text(
                            "↑${u.inputTokens} ↓${u.outputTokens}",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted
                        )
                    }
                    uiState.rateLimits.values.firstOrNull()?.let { r ->
                        val pct = (r.usedPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
                        if (uiState.tokenUsage != null) {
                            Box(Modifier.width(1.dp).height(10.dp).background(c.border))
                        }
                        // Bar
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.surface2)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(pct)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            pct > 0.85f -> c.red
                                            pct > 0.65f -> c.yellow
                                            else -> c.accent
                                        }
                                    )
                            )
                        }
                        Text(
                            "${r.usedPercent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted
                        )
                    }
                }
            }
        }

        if (uiState.showSettings) {
            SettingsSheet(viewModel = viewModel, onDismiss = { viewModel.toggleSettings() })
        }
    }

    // ─── Top toast for session switch ─────────────────────────────────────
    AnimatedVisibility(
        visible = switchToastMessage != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
        ) + fadeIn(tween(150)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(200)
        ) + fadeOut(tween(150)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 52.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface2.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, c.border.copy(alpha = 0.3f))
        ) {
            Text(
                switchToastMessage ?: "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // ─── Active sessions overlay ──────────────────────────────────────────
    AnimatedVisibility(
        visible = uiState.showActiveSessionsOverlay,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.fillMaxSize()
    ) {
        ActiveSessionsOverlay(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowActiveSessionsOverlay(false) }
        )
    }

    } // end Box
    } // end ModalNavigationDrawer
}

@Composable
private fun WelcomeSection(
    spacing: app.webcodex.codex.ui.theme.CodexSpacing,
    c: app.webcodex.codex.ui.theme.CodexColors,
    onHint: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.accentDim)
                .border(1.dp, c.accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_codex_brand),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(spacing.medium))
        Text(
            "What can I help with?",
            style = MaterialTheme.typography.titleLarge,
            color = c.text
        )
        Spacer(Modifier.height(spacing.small))
        Text(
	            "Ask anything about your codebase, or let Cortex make changes for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.text2
        )
        Spacer(Modifier.height(spacing.large))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "Explain architecture" to "Explain the architecture of this project",
                "Fix failing tests" to "Fix the failing tests",
                "Optimize code" to "Refactor for better performance"
            ).forEach { (label, hint) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(20.dp))
                        .clickable { onHint(hint) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MessageRow(
    msg: ChatMessage,
    viewModel: CodexViewModel,
    itemTexts: Map<String, String>
) {
    val c = LocalCodexColors.current
    val radius = CodexTheme.radius

    val content = when (msg.type) {
        MessageType.AGENT, MessageType.THINKING -> {
            val itemId = msg.id.removePrefix("a-").removePrefix("t-")
            itemTexts[itemId]?.ifEmpty { null } ?: msg.content
        }
        MessageType.EXEC -> {
            val itemId = msg.metadata["itemId"] as? String ?: ""
            itemTexts[itemId] ?: msg.content
        }
        else -> msg.content
    }

    when (msg.type) {

        // ─── User bubble ──────────────────────────────────────────────────────
        MessageType.USER -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .wrapContentWidth(Alignment.End)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1a2332), Color(0xFF162030))
                            )
                        )
                        .border(1.dp, Color(0xFF233552), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp))
                ) {
                    Text(
                        content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text
                    )
                }
            }
        }

        // ─── System pill ──────────────────────────────────────────────────────
        MessageType.SYSTEM -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surface2.copy(alpha = 0.6f))
                        .border(1.dp, c.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(content, style = MaterialTheme.typography.labelMedium, color = c.muted)
                }
            }
        }

        // ─── Agent message ────────────────────────────────────────────────────
        MessageType.AGENT -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Agent label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_codex_brand),
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "Cortex" + if (msg.isStreaming) " ●" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accent
                    )
                }
                if (msg.isStreaming && content.isEmpty()) {
                    Text("…", style = MaterialTheme.typography.bodyMedium, color = c.text)
                } else if (content.isNotEmpty()) {
                    MarkdownRichText(
                        content = content,
                        baseStyle = MaterialTheme.typography.bodyMedium,
                        textColor = c.text,
                        linkColor = c.blue,
                        applyPaddingAndBackground = false
                    )
                }
            }
        }

        // ─── Thinking block (collapsible) ─────────────────────────────────────
        MessageType.THINKING -> {
            var expanded by remember(msg.id) { mutableStateOf(msg.isStreaming) }
            val accentColor = c.accent

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = radius.medium, bottomEnd = radius.medium))
                    .drawBehind {
                        drawLine(
                            color = accentColor.copy(alpha = 0.7f),
                            start = Offset(1.5.dp.toPx(), 0f),
                            end = Offset(1.5.dp.toPx(), size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface2)
                        .clickable { expanded = !expanded }
                        .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (msg.isStreaming) "Thinking…" else "Thought",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Collapsible body
                AnimatedVisibility(visible = expanded && content.isNotEmpty()) {
                    MarkdownRichText(
                        content = content,
                        baseStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic
                        ),
                        textColor = c.text2,
                        linkColor = c.blue
                    )
                }
            }
        }

        // ─── Error ────────────────────────────────────────────────────────────
        MessageType.ERROR -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(radius.medium))
                    .background(c.redDim)
                    .border(1.dp, c.red.copy(alpha = 0.35f), RoundedCornerShape(radius.medium))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = c.red, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(content, style = MaterialTheme.typography.bodySmall, color = c.red)
            }
        }

        // ─── Stderr ───────────────────────────────────────────────────────────
        MessageType.STDERR -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(radius.medium))
                    .background(c.yellowDim)
                    .border(1.dp, c.yellow.copy(alpha = 0.3f), RoundedCornerShape(radius.medium))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = c.yellow
                )
            }
        }

        // ─── Permission card ──────────────────────────────────────────────────
        MessageType.PERMISSION -> {
            val reqId = msg.metadata["reqId"] as? String ?: ""
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(radius.medium))
                    .background(c.orangeDim)
                    .border(1.dp, c.orange.copy(alpha = 0.4f), RoundedCornerShape(radius.medium))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = c.orange, modifier = Modifier.size(14.dp))
                    Text("Permission required", style = MaterialTheme.typography.labelMedium, color = c.orange)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = c.text2
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermBtn("Approve", c.green, c.greenDim) { viewModel.sendPermission(reqId, "accept") }
                    PermBtn("Session", c.blue, c.blueDim) { viewModel.sendPermission(reqId, "acceptForSession") }
                    PermBtn("Deny", c.red, c.redDim) { viewModel.sendPermission(reqId, "decline") }
                }
            }
        }

        // ─── Exec card ────────────────────────────────────────────────────────
        MessageType.EXEC -> {
            val cmd = msg.metadata["command"] as? String ?: ""
            val ok = msg.metadata["ok"] as? Boolean
            val isRunning = msg.isStreaming
            var expanded by remember(msg.id) { mutableStateOf(false) }
            val dotColor = when {
                isRunning -> c.yellow
                ok == true -> c.green
                ok == false -> c.red
                else -> c.muted
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(radius.medium))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(radius.medium))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = content.isNotEmpty()) { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        "$ $cmd",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (content.isNotEmpty()) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = expanded && content.isNotEmpty()) {
                    Column {
                        HorizontalDivider(color = c.border)
                        Text(
                            content,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = c.text2
                        )
                    }
                }
            }
        }

        // ─── Tool (MCP) card ──────────────────────────────────────────────────
        MessageType.TOOL -> {
            var expanded by remember(msg.id) { mutableStateOf(false) }
            val resultJson = msg.metadata["item"] as? String
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(radius.medium))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(radius.medium))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = resultJson != null) { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = c.blue, modifier = Modifier.size(13.dp))
                    Text(
                        content.ifEmpty { "Tool call" },
                        style = MaterialTheme.typography.labelMedium,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (msg.isStreaming) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.yellow))
                    } else {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.green))
                    }
                    if (resultJson != null) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = expanded && resultJson != null) {
                    Column {
                        HorizontalDivider(color = c.border)
                        Text(
                            resultJson ?: "",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = c.text2
                        )
                    }
                }
            }
        }

        // ─── File change card ─────────────────────────────────────────────────
        MessageType.FILE -> {
            val itemJson = runCatching { JSONObject(msg.content) }.getOrNull()
                ?: runCatching {
                    JSONObject(msg.metadata["item"] as? String ?: "")
                }.getOrNull()
            val changes = itemJson?.optJSONArray("changes")

            if (changes != null && changes.length() > 0) {
                val fileChanges = (0 until changes.length()).mapNotNull { i ->
                    runCatching { changes.getJSONObject(i) }.getOrNull()?.let { obj ->
                        Triple(
                            obj.optString("path", "unknown"),
                            obj.optString("kind", "modified"),
                            obj.optString("diff", "")
                        )
                    }
                }
                val groupedByDir = fileChanges.groupBy { (path, _, _) ->
                    path.substringBeforeLast('/').ifEmpty { "/" }
                }.toSortedMap(compareBy { it })

                var cardExpanded by remember(msg.id) { mutableStateOf(true) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(radius.medium))
                ) {
                    // Card header: collapse/expand whole card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cardExpanded = !cardExpanded }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
                        Text(
                            "${fileChanges.size} file${if (fileChanges.size > 1) "s" else ""} changed",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (cardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AnimatedVisibility(visible = cardExpanded) {
                        Column {
                            HorizontalDivider(color = c.border)
                            groupedByDir.forEach { (dirPath, files) ->
                                FileChangeGroup(
                                    dirPath = dirPath,
                                    files = files,
                                    c = c
                                )
                            }
                        }
                    }
                }
            } else {
                // Fallback: plain path display
                val path = msg.metadata["path"] as? String
                    ?: itemJson?.optString("path")
                    ?: "file change"
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(radius.medium))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
                    Text(path, style = MaterialTheme.typography.labelMedium, color = c.text)
                }
            }
        }

        MessageType.PLAN -> {}
    }
}

@Composable
private fun PermBtn(label: String, color: Color, dimColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(dimColor)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun FileChangeGroup(
    dirPath: String,
    files: List<Triple<String, String, String>>,
    c: app.webcodex.codex.ui.theme.CodexColors
) {
    var groupExpanded by remember(dirPath) { mutableStateOf(files.size <= 3) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { groupExpanded = !groupExpanded }
                .background(c.surface3.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
            Text(
                dirPath,
                style = MaterialTheme.typography.labelSmall,
                color = c.text2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${files.size} file${if (files.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = c.muted
            )
            Icon(
                if (groupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(visible = groupExpanded) {
            Column {
                files.forEach { (path, kind, diff) ->
                    FileChangeTile(path = path, kind = kind, diff = diff, c = c)
                }
            }
        }
    }
}

@Composable
private fun FileChangeTile(
    path: String,
    kind: String,
    diff: String,
    c: app.webcodex.codex.ui.theme.CodexColors
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val kindColor = when (kind) {
        "added", "create" -> c.green
        "deleted", "remove" -> c.red
        else -> c.blue
    }
    val kindLabel = when (kind) {
        "added", "create" -> "+added"
        "deleted", "remove" -> "-deleted"
        else -> "~modified"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = diff.isNotEmpty()) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Kind badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(kindColor.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(kindLabel, style = MaterialTheme.typography.labelSmall, color = kindColor)
            }
            Text(
                path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (diff.isNotEmpty()) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        // Full path (muted, smaller)
        if (path.contains('/')) {
            Text(
                path,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(visible = expanded && diff.isNotEmpty()) {
            Column {
                HorizontalDivider(color = c.border)
                Text(
                    diff,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = c.text2
                )
            }
        }
    }
}
