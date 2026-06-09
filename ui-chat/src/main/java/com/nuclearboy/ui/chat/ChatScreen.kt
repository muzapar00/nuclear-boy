package com.nuclearboy.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuclearboy.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    projectId: String = "",
    initialMessage: String = "",
    onNavigateBack: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onNotification: ((String, String?) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(projectId) { viewModel.setProject(projectId) }
    // Auto-send initial message (from auto-project-creation flow)
    LaunchedEffect(initialMessage) {
        if (initialMessage.isNotEmpty()) {
            viewModel.sendMessage(initialMessage)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        android.util.Log.e("NuclearBoy", "[ChatScreen] ChatScreen composed projectId=$projectId")
        viewModel.notificationCallback = onNotification
    }
    val listState = rememberLazyListState()
    var showFiles by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(0) } // 0=Chat 1=Think 2=Expert
    var filePanelScrollState by remember { mutableStateOf(0f) }
    var filePanelScrollMax by remember { mutableStateOf(0f) }

    // File picker for attachments
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            android.util.Log.e("NuclearBoy", "[ChatScreen] filePicker selected uri=$uri")
            copyAttachedFile(context, it, viewModel)
        }
    }

    // Instant scroll to bottom on project switch (first load)
    LaunchedEffect(uiState.scrollToBottom) {
        if (uiState.messages.isNotEmpty()) {
            listState.requestScrollToItem(uiState.messages.lastIndex)
        }
    }
    // Follow new messages
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // Refresh files every time panel opens
    LaunchedEffect(showFiles) {
        android.util.Log.e("NuclearBoy", "[ChatScreen] filePanel showFiles=$showFiles")
        if (showFiles) viewModel.refreshProjectFiles()
    }

    BackHandler(enabled = showFiles) {
        android.util.Log.e("NuclearBoy", "[ChatScreen] BackHandler closing file panel")
        showFiles = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .semantics { contentDescription = "NUCLEAR BOY" },
        containerColor = NuclearBoyTheme.colorScheme.material.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Menu, "菜单", tint = NuclearBoyTheme.colorScheme.material.primary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    // 顶栏标题
                    if (projectId == "__general__") {
                        Text(
                            "☢️ 核弹男孩",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = NuclearBoyTheme.colorScheme.material.primary,
                        )
                    } else {
                        val projectName = viewModel.projectName.collectAsState().value
                        if (projectName.isNotEmpty()) {
                            Text(
                                text = projectName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF08090B),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NuclearBoyTheme.colorScheme.material.primary)
                                    .padding(horizontal = 8.dp, vertical = 1.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 文件面板按钮 — 类似首页设置图标
                    IconButton(
                        onClick = {
                            if (!showFiles) viewModel.refreshProjectFiles()
                            showFiles = !showFiles
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            "文件",
                            tint = if (showFiles) NuclearBoyTheme.colorScheme.material.primary
                                   else NuclearBoyTheme.colorScheme.material.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                TokenHudBar(
                    selectedMode = selectedMode,
                    onModeChange = { selectedMode = it; viewModel.setMode(it) },
                )
            }
        },
        bottomBar = {
            ChatInputBar(
                isProcessing = uiState.isProcessing,
                onSend = { text -> viewModel.sendMessage(text) },
                onCancel = { viewModel.cancelCurrentOperation() },
                fileCount = viewModel.projectFiles.collectAsState().value.size,
                placeholder = if (projectId == "__general__") "和核弹男孩对话…" else "输入指令…",
                onAttachFile = {
                    android.util.Log.e("NuclearBoy", "[ChatScreen] filePicker launched")
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
            )
        },
    ) { paddingValues ->
        if (uiState.messages.isEmpty()) {
            EmptyChatView(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onSuggestionClick = { text -> viewModel.sendMessage(text) },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                // General Agent 欢迎卡片
                if (projectId == "__general__" && uiState.messages.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = NuclearBoyTheme.colorScheme.material.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, NuclearBoyTheme.colorScheme.material.primary.copy(alpha = 0.2f)),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("☢️ 欢迎来到核弹男孩", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NuclearBoyTheme.colorScheme.material.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("我可以帮你：", fontSize = 14.sp, color = NuclearBoyTheme.colorScheme.material.onSurface)
                                Spacer(Modifier.height(8.dp))
                                WelcomeItem("🔍", "搜资料", "Bing + 百度双引擎搜索最新信息")
                                WelcomeItem("🐍", "写代码", "Python 3.11 沙箱 + Java 桥接控制手机")
                                WelcomeItem("📄", "生成文档", "Word / Excel / PPT 一键生成")
                                WelcomeItem("📁", "管项目", "多项目切换，文件浏览编辑")
                                WelcomeItem("📱", "控硬件", "闪光灯、闹钟、通知、日历… 全搞定")
                                Spacer(Modifier.height(12.dp))
                                Text("直接跟我说你想做什么，开干吧 💪", fontSize = 12.sp, color = NuclearBoyTheme.colorScheme.material.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(items = uiState.messages, key = { it.id }) { message ->
                    val isLast = message.id == uiState.messages.lastOrNull()?.id
                    val isStreaming = isLast && uiState.streamingState?.isStreaming == true

                    MessageBubble(
                        message = message,
                        isStreaming = isStreaming,
                        onRetry = {
                            if (message.role == MessageRole.ASSISTANT) viewModel.retryLastMessage()
                        },
                        onCopy = { text ->
                            copyToClipboard(context, text)
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }

        // ── Right-side File Drawer ──────────────────────
        AnimatedVisibility(
            visible = showFiles,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(250)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            ProjectFilePanel(
                files = viewModel.projectFiles.collectAsState().value,
                browseDir = viewModel.browseDir.collectAsState().value,
                projectRoot = viewModel.getProjectRoot(),
                onRefresh = { viewModel.refreshProjectFiles(viewModel.browseDir.value) },
                onNavigateTo = { viewModel.navigateToDir(it) },
                onNavigateUp = { viewModel.navigateUp() },
                context = context,
                onClose = { showFiles = false },
            )
        }
    } // Box
}

// ═══════════════════════════════════════════════════════════════════════
//  File Panel
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ProjectFilePanel(
    files: List<FileInfo>, browseDir: String, projectRoot: String,
    onRefresh: () -> Unit, onNavigateTo: (String) -> Unit, onNavigateUp: () -> Unit,
    context: Context,
    onClose: () -> Unit = {},
) {
    val nc = NuclearBoyTheme.colorScheme
    val fileListState = rememberLazyListState()

    // Preview state
    var showPreview by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<FileInfo?>(null) }
    var previewContent by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 260.dp, max = 320.dp)
            .fillMaxWidth(0.78f)
            .statusBarsPadding()
            .padding(bottom = 80.dp),  // avoid input bar overlap
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        color = nc.material.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
        border = BorderStroke(1.dp, nc.material.primary.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Header: title + prominent close button
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("📂 文件", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = nc.material.primary, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                // Prominent close button — avoid swipe-back conflict
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, nc.material.primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = nc.material.primary),
                ) {
                    Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("关闭", fontSize = 12.sp)
                }
            }
            // Breadcrumb
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(
                nc.material.primary.copy(alpha = 0.07f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Up button — always visible, disabled at root
                IconButton(onClick = onNavigateUp, enabled = browseDir != ".",
                    modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "上级目录", modifier = Modifier.size(20.dp),
                        tint = if (browseDir != ".") nc.material.primary else nc.material.onSurfaceVariant.copy(alpha = 0.3f))
                }
                Text("files/", style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    color = nc.material.primary))
                Text(browseDir, style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    color = nc.material.onSurfaceVariant),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, "刷新", modifier = Modifier.size(15.dp),
                        tint = nc.material.onSurfaceVariant)
                }
            }
            // File list
            if (files.isEmpty()) {
                Text("  空目录", style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace),
                    color = nc.material.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(state = fileListState, modifier = Modifier.fillMaxSize()) {
                        items(files, key = { it.path }) { file ->
                            FileRow(
                                file = file, projectRoot = projectRoot, context = context,
                                onClick = {
                                    if (file.isDirectory) onNavigateTo(file.name)
                                    else {
                                        android.util.Log.e("NuclearBoy", "[ChatScreen] preview file: ${file.name}")
                                        previewContent = null
                                        previewFile = file
                                        showPreview = true
                                    }
                                },
                            )
                        }
                    }
                    // Scroll indicator on the right edge
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(3.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(2.dp))
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════
            //  File preview dialog
            // ═══════════════════════════════════════════════════════════
            if (showPreview && previewFile != null) {
                // Load text content for previewable files
                LaunchedEffect(previewFile) {
                    val file = previewFile ?: return@LaunchedEffect
                    val ext = file.extension.lowercase()
                    val textExtensions = setOf("md", "txt", "py", "kt", "java", "js", "ts",
                        "json", "xml", "yaml", "yml", "gradle", "properties", "csv",
                        "html", "css", "sh", "bat", "ps1", "sql", "toml", "cfg", "ini", "log")
                    if (ext in textExtensions) {
                        try {
                            previewContent = java.io.File(file.path).readText()
                        } catch (e: Exception) {
                            try {
                                previewContent = java.io.File("${projectRoot}/${file.path}").readText()
                            } catch (e2: Exception) {
                                android.util.Log.e("NuclearBoy", "[ChatScreen] preview read error: ${e2.message}")
                                previewContent = null
                            }
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = {
                        showPreview = false
                        previewContent = null
                    },
                    title = {
                        Text(
                            previewFile!!.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = nc.material.onSurface
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            if (previewContent != null) {
                                SelectionContainer {
                                    Text(
                                        text = previewContent!!,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = nc.material.onSurface,
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    )
                                }
                            } else {
                                // Binary file or preview failed — show metadata
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("📄", fontSize = 32.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "无法预览此文件类型",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = nc.material.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${previewFile!!.extension.uppercase()} · ${previewFile!!.size.toFileSizeString()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = nc.material.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { shareFile(context, previewFile!!.path) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = nc.material.primary
                            )
                        ) {
                            Text("📤 分享")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPreview = false
                            previewContent = null
                        }) {
                            Text("关闭")
                        }
                    },
                    containerColor = nc.material.surface,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp,
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: FileInfo, projectRoot: String, context: Context,
    onClick: () -> Unit,
) {
    val nc = NuclearBoyTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon: folder or file type
        Text(
            text = if (file.isDirectory) "📁" else extIcon(file.extension),
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(file.name, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            color = if (file.isDirectory) nc.material.primary else nc.material.onSurface)
        Text(file.size.toFileSizeString(), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = nc.material.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

private fun extIcon(ext: String): String = when (ext.lowercase()) {
    "py" -> "🐍"; "kt", "java" -> "☕"; "js", "ts" -> "📜"; "json" -> "📋"
    "xml", "yaml", "yml" -> "⚙️"; "md", "txt" -> "📝"; "docx" -> "📄"
    "xlsx" -> "📊"; "pptx" -> "📽️"; "pdf" -> "📕"; "png", "jpg", "jpeg", "gif" -> "🖼️"
    "zip", "tar", "gz" -> "📦"; "html", "css" -> "🌐"
    else -> "📄"
}

// ═══════════════════════════════════════════════════════════════════════
//  File access — share path or open via FileProvider
// ═══════════════════════════════════════════════════════════════════════

private fun openSystemFileManager(context: Context, path: String) {
    try {
        val dir = File(path)
        val targetDir = if (dir.isDirectory) dir else dir.parentFile ?: return

        // Use our own FileProvider — works because files are in our app directory
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, targetDir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // Fallback: share the path as text so user can paste into file manager
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "项目目录")
            putExtra(Intent.EXTRA_TEXT, targetDir.absolutePath)
        }
        context.startActivity(Intent.createChooser(share, "复制路径后粘贴到文件管理器"))
    } catch (e: Exception) {
        Toast.makeText(context, "无法访问: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Empty State — Terminal/Hacker aesthetic
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyChatView(modifier: Modifier = Modifier, onSuggestionClick: (String) -> Unit = {}) {
    val nc = NuclearBoyTheme.colorScheme
    val greeting = LocalTime.now().toGreeting()

    // Animated scanline effect
    val scanlineOffset by rememberInfiniteTransition().animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
    )

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ASCII-art style logo
        Box(
            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp))
                .background(nc.material.surface)
                .border(2.dp, nc.material.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = ">_",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        color = nc.material.primary,
                    ),
                )
                Text(
                    text = "核弹",
                    fontSize = 14.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
                    color = nc.material.onSurface,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "NUCLEAR BOY",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, color = nc.material.primary,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "核 弹 男 孩",
            fontSize = 11.sp, fontFamily = FontFamily.Default,
            letterSpacing = 8.sp, color = nc.material.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Typing animation prompt
        val prompt = "guest@nuclear-boy:~$ _"
        TypingPrompt(text = prompt, color = nc.material.primary)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "$greeting。你的 AI 编程终端已就绪",
            style = MaterialTheme.typography.bodyMedium.copy(color = nc.material.onSurface, lineHeight = 22.sp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "写代码 · 读文件 · 执行脚本 · 生成文档",
            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            color = nc.material.onSurfaceVariant, letterSpacing = 1.sp,
        )

        Spacer(Modifier.height(28.dp))

        // Command suggestions — terminal style
        val suggestions = listOf(
            "> 创建一个 Python 项目，写个计算器",
            "> 分析并修复这段代码的问题",
            "> 生成本周工作总结的 Excel 表格",
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { cmd ->
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(nc.material.surface)
                        .border(2.dp, nc.material.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { onSuggestionClick(cmd.removePrefix("> ")) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            color = nc.material.onSurface.copy(alpha = 0.85f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeItem(emoji: String, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF00E676))
            Text(desc, fontSize = 11.sp, color = Color(0xFF838896))
        }
    }
}

@Composable
private fun TypingPrompt(text: String, color: Color) {
    var visibleChars by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..text.length) { visibleChars = i; delay(if (i == text.length) 2000 else 80) }
        while (true) {
            // Blink cursor
            visibleChars = text.length
            delay(500)
            visibleChars = text.length + 1 // Show cursor
            delay(500)
        }
    }
    val display = if (visibleChars > text.length) text else text.take(visibleChars)
    Text(
        text = "$display${if (visibleChars > text.length) "_" else ""}",
        fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = color.copy(alpha = 0.8f),
    )
}

// ═══════════════════════════════════════════════════════════════════════
//  Input Bar — Terminal prompt style
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ChatInputBar(
    isProcessing: Boolean, onSend: (String) -> Unit, onCancel: () -> Unit,
    fileCount: Int = 0,
    onAttachFile: (() -> Unit)? = null,
    placeholder: String = "输入指令…",
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val nc = NuclearBoyTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        color = nc.material.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        border = BorderStroke(2.dp, nc.material.outline.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Attach file button
            if (onAttachFile != null && !isProcessing) {
                IconButton(onClick = onAttachFile, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AttachFile, "添加附件", modifier = Modifier.size(18.dp),
                        tint = nc.material.onSurfaceVariant)
                }
            }

            // Terminal prompt
            Text(">", fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = if (isProcessing) nc.warning else nc.material.primary,
                modifier = Modifier.padding(start = 2.dp))

            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = nc.material.onSurfaceVariant.copy(alpha = 0.4f))) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = nc.material.onSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    cursorColor = nc.material.primary),
                shape = RoundedCornerShape(0.dp), maxLines = 4, singleLine = false,
            )

            if (isProcessing) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(nc.material.errorContainer)) {
                    Icon(Icons.Filled.Close, "停止", modifier = Modifier.size(18.dp), tint = nc.material.error)
                }
            } else {
                val canSend = text.isNotBlank()
                IconButton(
                    onClick = {
                        if (canSend) {
                            android.util.Log.e("NuclearBoy", "[ChatScreen] sendButton clicked textLen=${text.length}")
                            onSend(text); text = ""; focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(if (canSend) nc.material.primary else Color.Transparent)
                        .border(2.dp, if (canSend) nc.material.primary
                            else nc.material.outline.copy(alpha = 0.3f), CircleShape),
                ) {
                    Icon(Icons.Filled.Send, "发送", modifier = Modifier.size(16.dp),
                        tint = if (canSend) Color.Black else nc.material.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("NUCLEAR BOY", text))
}

private fun showNotification(context: Context, msg: String, project: String?) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val ch = android.app.NotificationChannel("nb_agent", "AI状态", android.app.NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
    }
    val intent = Intent(context, context.javaClass).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val isThinking = msg == "thinking"
    val title = if (project != null) "核弹男孩 · $project" else "核弹男孩"
    val notification = if (isThinking) {
        androidx.core.app.NotificationCompat.Builder(context, "nb_agent")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText("正在思考…")
            .setOngoing(true).setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW).build()
    } else {
        androidx.core.app.NotificationCompat.Builder(context, "nb_agent")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$title — 回复已就绪").setContentText(msg.take(120))
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(msg.take(500)))
            .setContentIntent(pending).setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT).build()
    }
    nm.notify(4201, notification)
}

private fun shareFile(context: Context, path: String) {
    try {
        val file = java.io.File(path)
        android.util.Log.e("NuclearBoy", "[ChatScreen] shareFile() path=$path")
        android.util.Log.e("NuclearBoy", "📎 shareFile path=$path exists=${file.exists()} abs=${file.absolutePath}")
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt", "py", "kt", "java", "js", "ts", "json", "xml", "yaml", "yml", "md" -> "text/plain"
            "png", "jpg", "jpeg", "gif", "webp" -> "image/*"
            "mp4", "avi", "mkv" -> "video/*"
            "mp3", "wav", "ogg" -> "audio/*"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享: ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun copyAttachedFile(context: Context, uri: Uri, viewModel: ChatViewModel) {
    try {
        val cr = context.contentResolver
        var fileName = "attachment"
        cr.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) fileName = cursor.getString(idx)
            }
        }
        val target = java.io.File(viewModel.getProjectRoot(), fileName)
        android.util.Log.e("NuclearBoy", "[ChatScreen] copyAttachedFile() fileName=$fileName target=${target.absolutePath}")
        cr.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        viewModel.refreshProjectFiles(viewModel.browseDir.value)
        android.util.Log.e("NuclearBoy", "[ChatScreen] copyAttachedFile() success: $fileName size=${target.length()}")
        Toast.makeText(context, "已添加: $fileName", Toast.LENGTH_SHORT).show()
        android.util.Log.e("NuclearBoy", "附件已添加: ${target.absolutePath}")
    } catch (e: Exception) {
        android.util.Log.e("NuclearBoy", "[ChatScreen] copyAttachedFile() error: ${e.message}", e)
        Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
