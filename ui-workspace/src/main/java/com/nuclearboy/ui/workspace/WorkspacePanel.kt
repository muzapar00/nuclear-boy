package com.nuclearboy.ui.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclearboy.common.FileInfo
import com.nuclearboy.common.toFileSizeString
import com.nuclearboy.ui.chat.NuclearBoyTheme

enum class PanelState { COLLAPSED, HALF, EXPANDED }

@Composable
fun WorkspacePanel(
    files: List<FileInfo> = emptyList(),
    modifiedFiles: List<String> = emptyList(),
    onFileClick: (FileInfo) -> Unit = {},
) {
    var panelState by remember { mutableStateOf(PanelState.COLLAPSED) }

    val height = when (panelState) {
        PanelState.COLLAPSED -> 48.dp
        PanelState.HALF -> 300.dp
        PanelState.EXPANDED -> 500.dp
    }

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = height),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
    ) {
        Column {
            IconButton(
                onClick = {
                    panelState = when (panelState) {
                        PanelState.COLLAPSED -> PanelState.HALF
                        PanelState.HALF -> PanelState.EXPANDED
                        PanelState.EXPANDED -> PanelState.COLLAPSED
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = when (panelState) {
                        PanelState.COLLAPSED -> Icons.Filled.ExpandLess
                        else -> Icons.Filled.ExpandMore
                    },
                    contentDescription = "展开/收起",
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = panelState != PanelState.COLLAPSED) {
                if (files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无文件", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(files) { file ->
                            val isModified = modifiedFiles.contains(file.path)
                            Surface(
                                onClick = { onFileClick(file) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = file.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = file.size.toFileSizeString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isModified) Text("✏️", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
