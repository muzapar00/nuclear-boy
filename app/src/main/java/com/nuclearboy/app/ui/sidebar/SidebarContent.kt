package com.nuclearboy.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclearboy.common.Project
import com.nuclearboy.common.SkillInfo

@Composable
fun SidebarContent(
    projects: List<Project>,
    currentProjectId: String?,
    activeSkills: List<SkillInfo> = emptyList(),
    onGeneralAgentSelected: () -> Unit,
    onProjectSelected: (String) -> Unit,
    onCreateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSkillManagerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClose: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    android.util.Log.e("NuclearBoy", "[Sidebar] SidebarContent composed projects=${projects.size} currentId=$currentProjectId activeSkills=${activeSkills.size}")

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = Color(0xFF0D0F12),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NUCLEAR BOY",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676),
                ),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "关闭", tint = Color(0xFF838896), modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Color(0xFF1E2230), thickness = 1.dp)

        Spacer(Modifier.height(8.dp))

        // General Agent — 绿底黑字
        val isGeneralActive = currentProjectId == "__general__"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF00E676))
                .clickable { onGeneralAgentSelected() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("☢️", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "核弹男孩对话",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF08090B),
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        // New project button
        OutlinedButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676)),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00E676).copy(alpha = 0.3f)),
            ),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("新建项目", fontSize = 13.sp, fontFamily = FontFamily.Default)
        }

        Spacer(Modifier.height(12.dp))

        // Project list — 绿框可滚动
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0C10)),
        ) {
            if (projects.isEmpty()) {
                Text(
                    "暂无项目",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = Color(0xFF838896),
                )
            } else {
                LazyColumn {
                    items(projects, key = { it.id }) { project ->
                        val isActive = project.id == currentProjectId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onProjectSelected(project.id)
                                }
                                .then(
                                    if (isActive) Modifier.background(Color(0xFF00E676).copy(alpha = 0.08f))
                                    else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp).height(24.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF00E676)),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    project.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) Color(0xFF00E676) else Color(0xFFE3E5E8),
                                    ),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { projectToDelete = project.id to project.name },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Default.Delete, "删除",
                                    tint = Color(0xFF4E515B),
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Skills — 管理按钮
        HorizontalDivider(color = Color(0xFF1E2230), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSkillManagerClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Build, "Skills", tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Skill 管理",
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = Color(0xFFE3E5E8),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${activeSkills.size} 个",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = if (activeSkills.isEmpty()) Color(0xFF4E515B) else Color(0xFF00E676),
            )
        }

        // Settings — 固定底部
        HorizontalDivider(color = Color(0xFF1E2230), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1D24))
                .clickable { onSettingsClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Settings, "设置", tint = Color(0xFF838896), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("设置 · API Key · 关于", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF838896))
        }

        // Footer
        Text(
            "核弹男孩 · mzpr00",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = Color(0xFF4E515B),
        )
    }

    // Delete confirmation
    projectToDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = Color(0xFF111318),
            title = { Text("删除项目", color = Color(0xFFE3E5E8)) },
            text = { Text("确定要删除「$name」吗？此操作不可撤销。", color = Color(0xFF838896)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(id)
                    projectToDelete = null
                }) { Text("删除", color = Color(0xFFFF453A)) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("取消", color = Color(0xFF838896))
                }
            },
        )
    }

    // Create project dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF111318),
            title = { Text("新建项目", color = Color(0xFFE3E5E8)) },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    placeholder = { Text("项目名称", color = Color(0xFF4E515B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color(0xFF2A2D35),
                        focusedTextColor = Color(0xFFE3E5E8),
                        unfocusedTextColor = Color(0xFFE3E5E8),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newProjectName.isNotBlank()) {
                            android.util.Log.e("NuclearBoy", "[Sidebar] createProject name='${newProjectName.trim()}' (keyboard done)")
                            onCreateProject(newProjectName.trim())
                            newProjectName = ""
                            showCreateDialog = false
                        }
                    }),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            android.util.Log.e("NuclearBoy", "[Sidebar] createProject name='${newProjectName.trim()}' (button click)")
                            onCreateProject(newProjectName.trim())
                            newProjectName = ""
                            showCreateDialog = false
                        }
                    },
                ) { Text("创建", color = Color(0xFF00E676)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消", color = Color(0xFF838896))
                }
            },
        )
    }
}
