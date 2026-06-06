@file:OptIn(ExperimentalFoundationApi::class)
package com.nuclearboy.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.TextView
import androidx.compose.ui.viewinterop.AndroidView
import com.nuclearboy.common.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onCopy: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val (alignment, bubbleColor, textColor, cornerShape) = bubbleAppearance(message.role, nuclearColors)
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    val showAvatar = message.role == MessageRole.ASSISTANT

    // Don't use fillMaxWidth + weight — it breaks alignment
    Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.semantics { contentDescription = "${message.role.label}消息" },
            horizontalArrangement = when {
                isUser -> Arrangement.End
                isSystem -> Arrangement.Center
                else -> Arrangement.Start
            },
            verticalAlignment = Alignment.Top,
        ) {
            if (showAvatar) {
                NuclearBoyAvatar(modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(6.dp))
            }

            if (isUser) Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 340.dp)
                    // Bold green border
                    .then(if (message.role == MessageRole.ASSISTANT)
                        Modifier.border(2.dp, nuclearColors.material.primary.copy(alpha = 0.25f), cornerShape)
                            .background(bubbleColor, cornerShape)
                    else Modifier.clip(cornerShape).background(bubbleColor))
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // Reasoning section
                val reasoning = message.reasoningContent
                if (!reasoning.isNullOrBlank()) {
                    ReasoningSection(reasoning = reasoning)
                }

                // Tool execution cards (expandable)
                if (message.toolCalls.isNotEmpty()) {
                    message.toolCalls.forEach { toolCall ->
                        ToolExecutionCard(toolCall = toolCall)
                    }
                }

                // Message content (selectable text)
                SelectionContainer {
                    MessageContent(
                        content = message.content,
                        isStreaming = isStreaming,
                        textColor = textColor,
                        messageStatus = message.status,
                    )
                }

                // File change cards
                if (message.fileChanges.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    message.fileChanges.forEach { change ->
                        FileChangeCard(fileChange = change)
                    }
                }

                // Copy full text button (non-streaming messages only)
                if (!isStreaming && message.content.isNotBlank()) {
                    var copied by remember { mutableStateOf(false) }
                    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("message", message.content))
                                copied = true
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(14.dp),
                                tint = textColor.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (copied) "已复制" else "复制", fontSize = 11.sp, color = textColor.copy(alpha = 0.5f))
                        }
                    }
                }

                // Status footer
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = message.timestamp.toRelativeTimeString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textColor.copy(alpha = 0.5f), fontSize = 10.sp,
                        ),
                    )
                    if (message.tokenUsage != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· ${message.tokenUsage?.totalTokens?.toInt()?.let { formatTokenCount(it) } ?: "?"} tokens",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textColor.copy(alpha = 0.4f), fontSize = 9.sp,
                            ),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    MessageStatusIcon(status = message.status, accentColor = nuclearColors.material.primary)
                }
            } // end of Column

            // Long-press menu attached to the bubble
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = { showMenu = false; onCopy?.invoke(message.content) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                )
                if (message.role == MessageRole.ASSISTANT && onRetry != null) {
                    DropdownMenuItem(
                        text = { Text("重新生成") },
                        onClick = { showMenu = false; onRetry() },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)) },
                    )
                }
            }

            if (!isUser) Spacer(Modifier.weight(1f))

        } // end of Row
    } // end of outer Box
}

// ── Avatar ───────────────────────────────────────────────────────────────────

@Composable
private fun NuclearBoyAvatar(modifier: Modifier = Modifier) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    Box(
        modifier = modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
            .background(nuclearColors.material.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "核",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = nuclearColors.material.onPrimaryContainer,
            ),
        )
    }
}

// ── Reasoning Section ────────────────────────────────────────────────────────

@Composable
private fun ReasoningSection(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    val nuclearColors = NuclearBoyTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(nuclearColors.codeBlockBackground)
            .border(2.dp, nuclearColors.codeBlockBorder, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = { expanded = !expanded })
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "思考过程",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                color = nuclearColors.material.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
            modifier = Modifier.size(16.dp),
            tint = nuclearColors.material.onSurfaceVariant,
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Text(
            text = reasoning,
            style = MaterialTheme.typography.bodySmall.copy(
                color = nuclearColors.material.onSurfaceVariant.copy(alpha = 0.85f),
                fontStyle = FontStyle.Italic, lineHeight = 18.sp,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    Spacer(Modifier.height(4.dp))
}

// ── Tool Execution Card (expandable) ─────────────────────────────────────────

@Composable
private fun ToolExecutionCard(toolCall: ToolCallRecord) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val hasOutput = !toolCall.output.isNullOrBlank()

    val statusColor = when (toolCall.status) {
        ToolCallStatus.PENDING -> nuclearColors.material.onSurfaceVariant
        ToolCallStatus.RUNNING -> nuclearColors.warning
        ToolCallStatus.COMPLETED -> nuclearColors.success
        ToolCallStatus.FAILED -> nuclearColors.material.error
        ToolCallStatus.CANCELLED -> nuclearColors.material.onSurfaceVariant
    }

    val statusText = when (toolCall.status) {
        ToolCallStatus.PENDING -> "等待"
        ToolCallStatus.RUNNING -> "执行中"
        ToolCallStatus.COMPLETED -> "完成"
        ToolCallStatus.FAILED -> "失败"
        ToolCallStatus.CANCELLED -> "取消"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(nuclearColors.codeBlockBackground)
            .border(2.dp, nuclearColors.codeBlockBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = if (hasOutput) Modifier.clickable { expanded = !expanded } else Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = toolCall.toolName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, color = nuclearColors.material.primary,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (hasOutput) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "展开输出",
                    modifier = Modifier.size(14.dp),
                    tint = nuclearColors.material.onSurfaceVariant,
                )
            }
        }

        // Expandable output
        AnimatedVisibility(
            visible = expanded && hasOutput,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp),
            ) {
                Text(
                    text = toolCall.output ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp, lineHeight = 15.sp, color = Color(0xFFB0B8C0),
                    ),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                )
            }
        }
    }
}

// ── Message Content ──────────────────────────────────────────────────────────

@Composable
private fun MessageContent(
    content: String, isStreaming: Boolean, textColor: Color, messageStatus: MessageStatus,
) {
    if (content.isBlank() && messageStatus == MessageStatus.THINKING) {
        ThinkingIndicator()
        return
    }

    if (isStreaming && content.isNotEmpty()) {
        StreamingText(text = content, textColor = textColor)
    } else {
        RichContentText(content = content, defaultColor = textColor)
    }
}

@Composable
private fun StreamingText(text: String, textColor: Color) {
    val nc = NuclearBoyTheme.colorScheme
    // Direct display from SSE stream — no artificial animation
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = textColor)) { append(text) }
        // Blinking cursor at end
        withStyle(SpanStyle(
            color = nc.material.primary, fontWeight = FontWeight.Bold,
        )) { append(" ▌") }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
    )
}

@Composable
private fun RichContentText(content: String, defaultColor: Color) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    val segments = parseContentForHighlighting(content)

    Column {
        segments.forEach { segment ->
            when (segment) {
                is ContentSegment.PlainText -> {
                    MarkdownText(text = segment.text, defaultColor = defaultColor)
                }
                is ContentSegment.CodeBlock -> {
                    CodeBlockCard(language = segment.language, code = segment.code)
                }
                is ContentSegment.InlineCode -> {
                    Text(
                        text = segment.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = nuclearColors.material.primary,
                            background = nuclearColors.codeBlockBackground,
                        ),
                    )
                }
            }
        }
    }
}

/** Renders markdown text using Markwon with table/strikethrough/tasklist support */
@Composable
private fun MarkdownText(text: String, defaultColor: Color) {
    val context = LocalContext.current
    val nc = NuclearBoyTheme.colorScheme

    val markwon = remember {
        io.noties.markwon.Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.ext.tasklist.TaskListPlugin.create(context))
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .build()
    }

    // Split text by $$...$$ math blocks
    val mathRegex = remember { Regex("\\$\\$([\\s\\S]*?)\\$\\$") }
    val parts = mathRegex.findAll(text).toList()

    if (parts.isEmpty()) {
        // No math — just Markwon
        AndroidView(
            factory = { ctx -> createMarkwonTextView(ctx, nc) },
            update = { tv -> markwon.setMarkdown(tv, text) },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // Interleave Markwon text and math blocks
        Column {
            var lastEnd = 0
            parts.forEach { match ->
                if (match.range.first > lastEnd) {
                    val before = text.substring(lastEnd, match.range.first)
                    AndroidView(
                        factory = { ctx -> createMarkwonTextView(ctx, nc) },
                        update = { tv -> markwon.setMarkdown(tv, before) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Math block — rendered as styled LaTeX source
                val formula = match.groupValues[1].trim()
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(nc.codeBlockBackground)
                        .border(1.dp, nc.material.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formula,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = nc.material.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                lastEnd = match.range.last + 1
            }
            if (lastEnd < text.length) {
                val after = text.substring(lastEnd)
                AndroidView(
                    factory = { ctx -> createMarkwonTextView(ctx, nc) },
                    update = { tv -> markwon.setMarkdown(tv, after) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun createMarkwonTextView(ctx: android.content.Context, nc: NuclearColorScheme): TextView {
    return TextView(ctx).apply {
        setTextColor(nc.material.onSurface.toArgb())
        textSize = 14f
        setLineSpacing(4f, 1f)
        setPadding(0, 4, 0, 4)
        setTextIsSelectable(true)
    }
}

/** Legacy manual parser — kept for streaming/compat. Not used for final rendering. */
@Composable
private fun LegacyMarkdownText(text: String, defaultColor: Color) {
    val nc = NuclearBoyTheme.colorScheme
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = nc.material.primary)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text.startsWith("*", i) && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = text.indexOf("*", i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // Inline code: `code`
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            color = nc.material.primary,
                            background = nc.codeBlockBackground,
                        )) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // Heading: ### text
                (text.startsWith("### ", i) || text.startsWith("## ", i) || text.startsWith("# ", i)) && (i == 0 || text[i - 1] == '\n') -> {
                    val level = if (text.startsWith("### ", i)) 3 else if (text.startsWith("## ", i)) 2 else 1
                    val start = i + level + 1
                    val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
                    appendLine()
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (18 - level * 2).sp,
                        color = nc.material.primary,
                    )) { append(text.substring(start, end)) }
                    appendLine()
                    i = end
                }
                else -> {
                    withStyle(SpanStyle(color = defaultColor)) { append(text[i]) }
                    i++
                }
            }
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
}

@Composable
private fun ThinkingIndicator() {
    val nuclearColors = NuclearBoyTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
            Box(
                modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(nuclearColors.thinkingDot.copy(alpha = alpha)),
            )
        }
    }
}

// ── File Change Card ─────────────────────────────────────────────────────────

@Composable
private fun FileChangeCard(fileChange: FileChange) {
    val nuclearColors = NuclearBoyTheme.colorScheme

    val (label, color) = when (fileChange.changeType) {
        ChangeType.CREATED -> "新建" to nuclearColors.success
        ChangeType.MODIFIED -> "修改" to nuclearColors.warning
        ChangeType.DELETED -> "删除" to nuclearColors.material.error
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(2.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = fileChange.filePath.substringAfterLast('/'),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp,
            ),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            ),
        )
    }
}

// ── Status Icon ──────────────────────────────────────────────────────────────

@Composable
private fun MessageStatusIcon(status: MessageStatus, accentColor: Color) {
    val nc = NuclearBoyTheme.colorScheme
    when (status) {
        MessageStatus.SENDING -> Text("...", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = nc.material.onSurfaceVariant)
        MessageStatus.SENT -> {}
        MessageStatus.COMPLETE -> {}
        MessageStatus.THINKING -> Text("thinking", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = nc.material.primary)
        MessageStatus.STREAMING -> Text("streaming", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = nc.material.primary)
        MessageStatus.EXECUTING -> Text("exec", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = nc.warning)
        MessageStatus.ERROR -> Text("error", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = nc.material.error)
        MessageStatus.CANCELLED -> Text("cancel", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = nc.material.onSurfaceVariant)
    }
}

// ── Content Parsing ──────────────────────────────────────────────────────────

sealed class ContentSegment {
    data class PlainText(val text: String) : ContentSegment()
    data class CodeBlock(val code: String, val language: String? = null) : ContentSegment()
    data class InlineCode(val code: String) : ContentSegment()
}

private fun parseContentForHighlighting(content: String): List<ContentSegment> {
    if (content.isBlank()) return listOf(ContentSegment.PlainText(content))

    val segments = mutableListOf<ContentSegment>()
    val codeBlockRegex = Regex("```(\\w+)?\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)

    var lastIndex = 0
    codeBlockRegex.findAll(content).forEach { match ->
        if (match.range.first > lastIndex) {
            val before = content.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) segments.add(ContentSegment.PlainText(before))
        }
        val language = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        val code = match.groupValues[2].trimEnd()
        segments.add(ContentSegment.CodeBlock(code, language))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex)
        if (remaining.isNotBlank()) segments.add(ContentSegment.PlainText(remaining))
    }

    return if (segments.isEmpty()) listOf(ContentSegment.PlainText(content)) else segments
}

// ── Code Block Card ──────────────────────────────────────────────────────────

@Composable
private fun CodeBlockCard(language: String?, code: String) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(nuclearColors.codeBlockBackground)
            .border(2.dp, nuclearColors.codeBlockBorder, RoundedCornerShape(10.dp)),
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(nuclearColors.codeBlockBorder.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language ?: "code",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = nuclearColors.material.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = if (copied) "已复制" else "复制代码",
                modifier = Modifier
                    .size(18.dp)
                    .clickable {
                        if (!copied) {
                            val clipboard =
                                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                            copied = true
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    },
                tint = if (copied) nuclearColors.success else nuclearColors.material.onSurfaceVariant,
            )
        }
        // Code content with syntax highlighting + scroll
        val highlighted = remember(code, language) { highlightCode(code, language) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun bubbleAppearance(role: MessageRole, colors: NuclearColorScheme): BubbleAppearance {
    return when (role) {
        MessageRole.TOOL -> BubbleAppearance(
            Arrangement.Center, colors.systemBubble, colors.material.onSurface, RoundedCornerShape(12.dp),
        )
        MessageRole.USER -> BubbleAppearance(
            Arrangement.End, colors.userBubble, colors.userBubbleText, NuclearShapes.ChatBubbleUser,
        )
        MessageRole.ASSISTANT -> BubbleAppearance(
            Arrangement.Start, colors.assistantBubble, colors.material.onSurface, NuclearShapes.ChatBubbleAssistant,
        )
        MessageRole.SYSTEM -> BubbleAppearance(
            Arrangement.Center, colors.systemBubble, colors.material.onSurfaceVariant, NuclearShapes.Medium,
        )
    }
}

private data class BubbleAppearance(
    val alignment: Arrangement.Horizontal, val bubbleColor: Color,
    val textColor: Color, val cornerShape: RoundedCornerShape,
)

private fun formatTokenCount(count: Int): String = when {
    count >= 1000 -> "${"%.1f".format(count / 1000.0)}k"
    else -> count.toString()
}

private val MessageRole.label: String
    get() = when (this) {
        MessageRole.TOOL -> "工具"
        MessageRole.USER -> "用户"
        MessageRole.ASSISTANT -> "核弹男孩"
        MessageRole.SYSTEM -> "系统"
    }

// ── Syntax Highlighting ──────────────────────────────────────────────────────

private val KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed",
    "when", "if", "else", "for", "while", "do", "return", "break", "continue",
    "import", "package", "public", "private", "protected", "internal", "override",
    "suspend", "inline", "operator", "infix", "tailrec", "external", "annotation",
    "companion", "const", "lateinit", "by", "in", "out", "is", "as", "null", "true", "false",
)

private val PY_KEYWORDS = setOf("def", "class", "import", "from", "return", "if", "elif",
    "else", "for", "while", "try", "except", "finally", "with", "as", "in", "not", "and",
    "or", "True", "False", "None", "print", "lambda", "yield", "raise", "pass", "break",
    "continue", "global", "nonlocal", "assert", "del", "is")

private fun highlightCode(code: String, language: String?): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val kw = when (language?.lowercase()) {
            "py", "python" -> PY_KEYWORDS
            else -> KEYWORDS
        }
        val commentColor = Color(0xFF6A9955)
        val stringColor = Color(0xFFCE9178)
        val keywordColor = Color(0xFF569CD6)
        val numberColor = Color(0xFFB5CEA8)
        val funcColor = Color(0xFFDCDCAA)
        val defaultColor = Color(0xFFD4D4D4)

        var i = 0
        while (i < code.length) {
            when {
                code.startsWith("//", i) || code.startsWith("#", i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it + 1 }
                    withStyle(SpanStyle(color = commentColor)) { append(code.substring(i, end)) }
                    i = end
                }
                code[i] == '"' -> {
                    val end = findStringEnd(code, i, '"')
                    withStyle(SpanStyle(color = stringColor)) { append(code.substring(i, end)) }
                    i = end
                }
                code[i] == '\'' -> {
                    val end = findStringEnd(code, i, '\'')
                    withStyle(SpanStyle(color = stringColor)) { append(code.substring(i, end)) }
                    i = end
                }
                code[i].isDigit() && (i == 0 || !code[i-1].isLetter()) -> {
                    val start = i
                    while (i < code.length && (code[i].isDigit() || code[i] == '.')) i++
                    withStyle(SpanStyle(color = numberColor)) { append(code.substring(start, i)) }
                }
                code[i].isLetter() || code[i] == '_' -> {
                    val start = i
                    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                    val word = code.substring(start, i)
                    if (word in kw) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) { append(word) }
                    } else if (i < code.length && code[i] == '(') {
                        withStyle(SpanStyle(color = funcColor)) { append(word) }
                    } else {
                        withStyle(SpanStyle(color = defaultColor)) { append(word) }
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = defaultColor)) { append(code[i]) }
                    i++
                }
            }
        }
    }
}

private fun findStringEnd(code: String, start: Int, quote: Char): Int {
    var i = start + 1
    while (i < code.length) {
        if (code[i] == '\\') { i += 2; continue }
        if (code[i] == quote) return i + 1
        i++
    }
    return code.length
}
