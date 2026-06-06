package com.nuclearboy.ui.workspace

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import com.nuclearboy.ui.chat.NuclearBoyTheme

// ── Diff Models ─────────────────────────────────────────────────────────────

/** The mode of diff presentation. */
enum class DiffMode {
    /** Traditional unified diff: all hunks stacked vertically. */
    UNIFIED,

    /** Side-by-side: old on the left, new on the right. */
    SIDE_BY_SIDE,
}

/** Represents the type of a single diff line. */
enum class DiffLineType {
    HEADER,    // @@ ... @@ hunk header
    CONTEXT,   // Unchanged context line
    ADDITION,  // + line (green)
    DELETION,  // - line (red)
    META,      // --- or +++ or diff metadata
}

/** A single annotated line in the diff output. */
data class DiffLine(
    val type: DiffLineType,
    val oldLineNumber: Int? = null,   // null for additions
    val newLineNumber: Int? = null,   // null for deletions
    val content: String,
)

/** Collection of diff lines that form a hunk or a chunk. */
data class DiffHunk(
    val header: String? = null,
    val lines: List<DiffLine>,
)

// ── Diff Viewer Composable ──────────────────────────────────────────────────

/**
 * Renders a code diff in either unified or side-by-side mode.
 *
 * Uses green backgrounds for additions, red for deletions, and displays
 * line numbers for both old and new versions.
 *
 * @param diffText     Raw unified-diff formatted text to parse and render.
 * @param mode         Display mode: [DiffMode.UNIFIED] or [DiffMode.SIDE_BY_SIDE].
 * @param fileName     Name of the file being diffed (shown in header).
 * @param modifier     Optional [Modifier].
 */
@Composable
fun DiffViewer(
    diffText: String,
    mode: DiffMode = DiffMode.UNIFIED,
    fileName: String = "",
    modifier: Modifier = Modifier,
) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    val isDark = nuclearColors.isDark

    val parsedHunks = remember(diffText) { parseUnifiedDiff(diffText) }

    // Diff color palette
    val additionBg = if (isDark) Color(0xFF1B3A1B) else Color(0xFFE6FFE6)
    val additionBorder = if (isDark) Color(0xFF2E7D32) else Color(0xFF4CAF50)
    val deletionBg = if (isDark) Color(0xFF3A1A1A) else Color(0xFFFFE6E6)
    val deletionBorder = if (isDark) Color(0xFFC62828) else Color(0xFFFF5252)
    val headerBg = if (isDark) Color(0xFF1A2A3A) else Color(0xFFE3F2FD)
    val contextBg = Color.Transparent
    val lineNumberColor = if (isDark) Color(0xFF888888) else Color(0xFF9E9E9E)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(nuclearColors.codeBlockBackground)
            .semantics { contentDescription = "差异对比: $fileName" },
    ) {
        // ── Header bar ────────────────────────────────────────────────
        DiffHeader(
            fileName = fileName,
            mode = mode,
            additionCount = parsedHunks.sumOf { hunk -> hunk.lines.count { it.type == DiffLineType.ADDITION } },
            deletionCount = parsedHunks.sumOf { hunk -> hunk.lines.count { it.type == DiffLineType.DELETION } },
        )

        HorizontalDivider(color = nuclearColors.material.surfaceVariant.copy(alpha = 0.3f))

        // ── Diff content ──────────────────────────────────────────────
        when (mode) {
            DiffMode.UNIFIED -> {
                UnifiedDiffView(
                    hunks = parsedHunks,
                    additionBg = additionBg,
                    additionBorder = additionBorder,
                    deletionBg = deletionBg,
                    deletionBorder = deletionBorder,
                    headerBg = headerBg,
                    contextBg = contextBg,
                    lineNumberColor = lineNumberColor,
                )
            }
            DiffMode.SIDE_BY_SIDE -> {
                SideBySideDiffView(
                    hunks = parsedHunks,
                    additionBg = additionBg,
                    additionBorder = additionBorder,
                    deletionBg = deletionBg,
                    deletionBorder = deletionBorder,
                    headerBg = headerBg,
                    contextBg = contextBg,
                    lineNumberColor = lineNumberColor,
                )
            }
        }
    }
}

// ── Diff Header ────────────────────────────────────────────────────────────

@Composable
private fun DiffHeader(
    fileName: String,
    mode: DiffMode,
    additionCount: Int,
    deletionCount: Int,
) {
    val nuclearColors = NuclearBoyTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "📄 $fileName",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 1,
        )

        Spacer(Modifier.width(16.dp))

        // Stats
        Text(
            text = "+$additionCount",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "-$deletionCount",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFFFF5252),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = if (mode == DiffMode.UNIFIED) "统一视图" else "并排视图",
            style = MaterialTheme.typography.labelSmall.copy(
                color = nuclearColors.material.onSurfaceVariant,
            ),
        )
    }
}

// ── Unified Diff View ──────────────────────────────────────────────────────

@Composable
private fun UnifiedDiffView(
    hunks: List<DiffHunk>,
    additionBg: Color,
    additionBorder: Color,
    deletionBg: Color,
    deletionBorder: Color,
    headerBg: Color,
    contextBg: Color,
    lineNumberColor: Color,
) {
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp),
    ) {
        hunks.forEach { hunk ->
            // Hunk header
            if (hunk.header != null) {
                item(key = hunk.header) {
                    HunkHeaderRow(header = hunk.header, backgroundColor = headerBg)
                }
            }

            items(
                items = hunk.lines,
                key = { "u_${it.oldLineNumber}_${it.newLineNumber}_${it.content.take(20)}" },
            ) { line ->
                val bg = when (line.type) {
                    DiffLineType.ADDITION -> additionBg
                    DiffLineType.DELETION -> deletionBg
                    DiffLineType.HEADER, DiffLineType.META -> headerBg
                    DiffLineType.CONTEXT -> contextBg
                }
                val leftBorderColor = when (line.type) {
                    DiffLineType.ADDITION -> additionBorder
                    DiffLineType.DELETION -> deletionBorder
                    else -> Color.Transparent
                }

                UnifiedDiffRow(
                    line = line,
                    backgroundColor = bg,
                    leftBorderColor = leftBorderColor,
                    lineNumberColor = lineNumberColor,
                )
            }
        }
    }
}

@Composable
private fun HunkHeaderRow(header: String, backgroundColor: Color) {
    val nuclearColors = NuclearBoyTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = nuclearColors.material.primary,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun UnifiedDiffRow(
    line: DiffLine,
    backgroundColor: Color,
    leftBorderColor: Color,
    lineNumberColor: Color,
) {
    val prefix = when (line.type) {
        DiffLineType.ADDITION -> "+"
        DiffLineType.DELETION -> "-"
        DiffLineType.HEADER, DiffLineType.META -> ""
        DiffLineType.CONTEXT -> " "
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(
                if (leftBorderColor != Color.Transparent) {
                    Modifier.border(width = 3.dp, color = leftBorderColor, shape = RoundedCornerShape(0.dp))
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        // Old line number
        Text(
            text = line.oldLineNumber?.toString()?.padStart(4) ?: "    ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = lineNumberColor,
                fontSize = 10.sp,
            ),
        )

        Spacer(Modifier.width(4.dp))

        // New line number
        Text(
            text = line.newLineNumber?.toString()?.padStart(4) ?: "    ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = lineNumberColor,
                fontSize = 10.sp,
            ),
        )

        Spacer(Modifier.width(8.dp))

        // Prefix
        Text(
            text = prefix,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = when (line.type) {
                    DiffLineType.ADDITION -> Color(0xFF4CAF50)
                    DiffLineType.DELETION -> Color(0xFFFF5252)
                    else -> lineNumberColor
                },
                fontSize = 11.sp,
            ),
        )

        // Content
        Text(
            text = line.content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

// ── Side-by-Side Diff View ──────────────────────────────────────────────────

@Composable
private fun SideBySideDiffView(
    hunks: List<DiffHunk>,
    additionBg: Color,
    additionBorder: Color,
    deletionBg: Color,
    deletionBorder: Color,
    headerBg: Color,
    contextBg: Color,
    lineNumberColor: Color,
) {
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp),
    ) {
        hunks.forEach { hunk ->
            if (hunk.header != null) {
                item {
                    // Hunk header spans full width
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = hunk.header,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NuclearBoyTheme.colorScheme.material.primary,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }

            // Side-by-side pairs: group additions next to deletions
            val paired = pairLines(hunk.lines)

            items(
                items = paired,
                key = { "ss_${it.first?.oldLineNumber}_${it.first?.newLineNumber}_${it.first?.content?.take(20)}" },
            ) { pair ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left side (old)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min),
                    ) {
                        pair.first?.let { line ->
                            SideDiffLine(
                                line = line,
                                isOldSide = true,
                                additionBg = additionBg,
                                deletionBg = deletionBg,
                                contextBg = contextBg,
                                lineNumberColor = lineNumberColor,
                            )
                        }
                    }

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(NuclearBoyTheme.colorScheme.material.surfaceVariant.copy(alpha = 0.5f)),
                    )

                    // Right side (new)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min),
                    ) {
                        pair.second?.let { line ->
                            SideDiffLine(
                                line = line,
                                isOldSide = false,
                                additionBg = additionBg,
                                deletionBg = deletionBg,
                                contextBg = contextBg,
                                lineNumberColor = lineNumberColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideDiffLine(
    line: DiffLine,
    isOldSide: Boolean,
    additionBg: Color,
    deletionBg: Color,
    contextBg: Color,
    lineNumberColor: Color,
) {
    val bg = when {
        line.type == DiffLineType.ADDITION && !isOldSide -> additionBg
        line.type == DiffLineType.DELETION && isOldSide -> deletionBg
        line.type == DiffLineType.CONTEXT -> contextBg
        else -> Color.Transparent
    }

    val prefix = when {
        line.type == DiffLineType.ADDITION -> "+"
        line.type == DiffLineType.DELETION -> "-"
        else -> " "
    }

    val lineNum = if (isOldSide) line.oldLineNumber else line.newLineNumber

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = lineNum?.toString()?.padStart(4) ?: "    ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = lineNumberColor,
                fontSize = 10.sp,
            ),
        )
        Text(
            text = prefix,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = when (line.type) {
                    DiffLineType.ADDITION -> Color(0xFF4CAF50)
                    DiffLineType.DELETION -> Color(0xFFFF5252)
                    else -> lineNumberColor
                },
                fontSize = 11.sp,
            ),
        )
        Text(
            text = line.content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

// ── Diff Parsing ────────────────────────────────────────────────────────────

/**
 * Parses a standard unified-diff format string into structured [DiffHunk]s.
 *
 * Format expected:
 * ```
 * --- a/file.kt
 * +++ b/file.kt
 * @@ -start,count +start,count @@
 *  context line
 * -deleted line
 * +added line
 * ```
 */
private fun parseUnifiedDiff(diff: String): List<DiffHunk> {
    val hunks = mutableListOf<DiffHunk>()
    val lines = diff.lines()

    var oldLineNum = 1
    var newLineNum = 1
    var currentHunkLines = mutableListOf<DiffLine>()
    var pendingMeta = mutableListOf<DiffLine>()
    var currentHeader: String? = null

    val hunkHeaderRegex = Regex("""@@\s*-(\d+)(?:,(\d+))?\s*\+(\d+)(?:,(\d+))?\s*@@(.*)""")

    for (line in lines) {
        when {
            // Hunk header: @@ -X,Y +A,B @@
            line.startsWith("@@") -> {
                // Save previous hunk if not empty
                if (currentHunkLines.isNotEmpty()) {
                    hunks.add(DiffHunk(header = currentHeader, lines = currentHunkLines.toList()))
                }
                currentHunkLines = mutableListOf()
                currentHeader = line

                val match = hunkHeaderRegex.find(line)
                if (match != null) {
                    oldLineNum = match.groupValues[1].toInt()
                    newLineNum = match.groupValues[3].toInt()
                }
            }
            // Metadata: --- or +++
            line.startsWith("---") || line.startsWith("+++") -> {
                pendingMeta.add(DiffLine(
                    type = DiffLineType.META,
                    content = line,
                ))
            }
            // Context line (starts with space or empty)
            line.startsWith(" ") -> {
                currentHunkLines.add(DiffLine(
                    type = DiffLineType.CONTEXT,
                    oldLineNumber = oldLineNum,
                    newLineNumber = newLineNum,
                    content = line.drop(1),
                ))
                oldLineNum++
                newLineNum++
            }
            // Addition
            line.startsWith("+") && !line.startsWith("+++") -> {
                currentHunkLines.add(DiffLine(
                    type = DiffLineType.ADDITION,
                    oldLineNumber = null,
                    newLineNumber = newLineNum,
                    content = line.drop(1),
                ))
                newLineNum++
            }
            // Deletion
            line.startsWith("-") && !line.startsWith("---") -> {
                currentHunkLines.add(DiffLine(
                    type = DiffLineType.DELETION,
                    oldLineNumber = oldLineNum,
                    newLineNumber = null,
                    content = line.drop(1),
                ))
                oldLineNum++
            }
            // Other: treat as context
            line.isNotBlank() -> {
                currentHunkLines.add(DiffLine(
                    type = DiffLineType.CONTEXT,
                    content = line,
                ))
            }
        }
    }

    // Save last hunk
    if (currentHunkLines.isNotEmpty()) {
        hunks.add(DiffHunk(header = currentHeader, lines = currentHunkLines.toList()))
    }

    return hunks.ifEmpty {
        // If nothing parsed, treat the entire text as a single context hunk
        listOf(DiffHunk(
            lines = diff.lines().map { DiffLine(type = DiffLineType.CONTEXT, content = it) },
        ))
    }
}

// ── Side-by-Side Pairing ────────────────────────────────────────────────────

/**
 * Pairs lines for side-by-side display: deletions on the left, additions
 * on the right. Context lines appear on both sides.
 */
private fun pairLines(lines: List<DiffLine>): List<Pair<DiffLine?, DiffLine?>> {
    val result = mutableListOf<Pair<DiffLine?, DiffLine?>>()
    var i = 0
    while (i < lines.size) {
        val current = lines[i]
        when (current.type) {
            DiffLineType.DELETION -> {
                // Look ahead for an addition to pair with
                val next = lines.getOrNull(i + 1)
                if (next?.type == DiffLineType.ADDITION) {
                    result.add(current to next)
                    i += 2
                } else {
                    result.add(current to null)
                    i++
                }
            }
            DiffLineType.ADDITION -> {
                // Look back: if previous was a deletion, it's already paired
                // If not, add as standalone
                val prev = result.lastOrNull()
                if (prev != null && prev.first?.type == DiffLineType.DELETION && prev.second == null) {
                    // Patch the previous entry
                    result[result.lastIndex] = prev.first to current
                    i++
                } else {
                    result.add(null to current)
                    i++
                }
            }
            else -> {
                // Context, header, meta: show on both sides
                result.add(current to current)
                i++
            }
        }
    }
    return result
}

// ── Preview ─────────────────────────────────────────────────────────────────

fun previewDiffText(): String = """--- a/ChatViewModel.kt
+++ b/ChatViewModel.kt
@@ -12,7 +12,8 @@ class ChatViewModel @Inject constructor(
     private val agentEngine: AgentEngine,
     private val tokenTracker: TokenTracker,
     private val contextManager: ContextWindowManager,
+    private val analytics: AnalyticsTracker,
 ) : ViewModel() {
-    val messages: StateFlow<List<ChatMessage>>
+    val messages: StateFlow<List<ChatMessage>> = MutableStateFlow(emptyList())
     val isProcessing: StateFlow<Boolean>
@@ -25,6 +26,10 @@ class ChatViewModel @Inject constructor(
         observeContextManager()
     }

+    fun logAnalytics(event: String) {
+        analytics.track(event)
+    }
+
     fun sendMessage(text: String) {
         val trimmed = text.trim()
@@ -45,7 +50,7 @@ class ChatViewModel @Inject constructor(
         _isProcessing.value = true

-        val placeholder = ChatMessage(id = UUID.randomUUID().toString(), ...)
+        val placeholder = ChatMessage(id = UUID.randomUUID().toString(), role = MessageRole.ASSISTANT, ...)
         _messages.update { it + placeholder }
""".trimMargin()
