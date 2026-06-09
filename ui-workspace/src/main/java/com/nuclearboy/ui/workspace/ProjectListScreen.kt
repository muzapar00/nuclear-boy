package com.nuclearboy.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclearboy.common.Project
import com.nuclearboy.common.toRelativeTimeString
import com.nuclearboy.ui.chat.NuclearBoyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    projects: List<Project> = emptyList(),
    onMenuClick: () -> Unit = {},
    onProjectSelected: (String) -> Unit = {},
    onCreateProject: (String) -> Unit = {},
    onDeleteProject: (String) -> Unit = {},
    onAutoCreateProject: (String) -> Unit = {},  // message -> creates project and navigates
    onSettingsClick: () -> Unit = {},
    welcomeText: String? = null,
    onClearWelcome: () -> Unit = {},
) {
    val nc = NuclearBoyTheme.colorScheme
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var quickInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var showWelcomeDialog by remember { mutableStateOf(false) }
    // 当 welcomeText 异步加载完毕后自动弹窗
    LaunchedEffect(welcomeText) {
        if (welcomeText != null) showWelcomeDialog = true
    }

    Scaffold(
        containerColor = nc.material.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NUCLEAR BOY",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = nc.material.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "菜单", tint = nc.material.primary)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "设置", tint = nc.material.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = nc.material.background,
                ),
            )
        },
        bottomBar = {
            // Quick-input bar for auto project creation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = nc.material.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ">", fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, color = nc.material.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = quickInput,
                        onValueChange = { quickInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "直接描述需求，自动创建项目…",
                                fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                                color = nc.material.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            color = nc.material.onSurface,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = nc.material.primary,
                        ),
                        shape = RoundedCornerShape(0.dp),
                        maxLines = 2, singleLine = false,
                    )
                    val canSend = quickInput.isNotBlank()
                    IconButton(
                        onClick = {
                            if (canSend) {
                                val msg = quickInput
                                quickInput = ""
                                focusManager.clearFocus()
                                onAutoCreateProject(msg)
                            }
                        },
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(if (canSend) nc.material.primary else Color.Transparent)
                            .border(2.dp, if (canSend) nc.material.primary
                                else nc.material.outline.copy(alpha = 0.3f), CircleShape),
                    ) {
                        Icon(Icons.Default.Send, "发送", modifier = Modifier.size(16.dp),
                            tint = if (canSend) Color.Black else nc.material.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = nc.material.primary,
                contentColor = Color.Black,
            ) {
                Icon(Icons.Default.Add, "新建项目")
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            // Welcome section — always visible
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(">_", fontSize = 36.sp, fontFamily = FontFamily.Monospace,
                    color = nc.material.primary.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                Text("核弹男孩", fontSize = 14.sp, color = nc.material.primary,
                    fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(2.dp))
                Text("在下方输入需求，自动创建项目并进入聊天",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = nc.material.onSurfaceVariant.copy(alpha = 0.5f))
            }

            // Project list — shown below if projects exist
            if (projects.isNotEmpty()) {
                Text(
                    "项目 (${projects.size})",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = nc.material.onSurfaceVariant, fontWeight = FontWeight.Bold,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                        project = project,
                        onClick = {
                            onProjectSelected(project.id)
                        },
                        onDelete = {
                            projectToDelete = Pair(project.id, project.name)
                        },
                    )
                }
            }
        }
        }
    }

    // Create dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = nc.material.surface,
            title = { Text("新建项目", color = nc.material.onSurface) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("项目名称") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = nc.material.primary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onCreateProject(name.trim())
                        showCreateDialog = false
                    }
                }) { Text("创建", color = nc.material.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消", color = nc.material.onSurfaceVariant)
                }
            },
        )
    }

    // Welcome dialog
    if (showWelcomeDialog && welcomeText != null) {
        AlertDialog(
            onDismissRequest = { showWelcomeDialog = false; onClearWelcome() },
            containerColor = nc.material.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("☢️ 欢迎回来！", color = nc.material.primary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    welcomeText,
                    color = nc.material.onSurface,
                    lineHeight = 22.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWelcomeDialog = false
                    onClearWelcome()
                }) { Text("开始干活 💪", color = nc.material.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showWelcomeDialog = false; onClearWelcome() }) {
                    Text("关闭", color = nc.material.onSurfaceVariant)
                }
            },
        )
    }

    // Delete confirmation
    projectToDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = nc.material.surface,
            title = { Text("删除项目", color = nc.material.error) },
            text = { Text("确定要删除「$name」吗？项目文件夹和聊天记录将被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(id)
                    projectToDelete = null
                }) { Text("删除", color = nc.material.error) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val nc = NuclearBoyTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = nc.material.surface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(nc.material.outline),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = nc.material.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(14.dp),
                        tint = nc.material.error.copy(alpha = 0.5f))
                }
            }
            if (project.techStack.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    project.techStack.take(3).forEach { tech ->
                        Text(
                            tech,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = nc.material.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                project.lastOpenedAt.toRelativeTimeString(),
                fontSize = 10.sp,
                color = nc.material.onSurfaceVariant,
            )
        }
    }
}
