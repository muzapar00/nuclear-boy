package com.nuclearboy.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclearboy.api.deepseek.ContextBudget
import com.nuclearboy.api.deepseek.ContextWarningLevel

@Composable
fun TokenHudBar(
    selectedMode: Int = 0,
    onModeChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bg = NuclearBoyTheme.colorScheme.tokenBarBackground
    val nc = NuclearBoyTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mode dropdown
        val modes = listOf("聊天", "思考", "专家")
        var expanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(nc.material.primary.copy(alpha = 0.08f))
                    .clickable { expanded = true }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(com.nuclearboy.ui.chat.R.drawable.deepseek_logo),
                    contentDescription = "DeepSeek",
                    modifier = Modifier.height(18.dp).widthIn(max = 120.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = modes[selectedMode.coerceIn(0, 2)],
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = nc.material.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowDropDown, "选择", Modifier.size(14.dp), tint = nc.material.primary.copy(alpha = 0.5f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                containerColor = nc.material.surface,
            ) {
                modes.forEachIndexed { idx, label ->
                    val desc = when (idx) { 0 -> "快速应答 · Flash" 1 -> "深度思考 · Pro" 2 -> "极致推理 · Pro" else -> "" }
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .then(if (idx == selectedMode) Modifier.background(nc.material.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp)) else Modifier)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nc.material.onSurface)
                                Text(desc, fontSize = 10.sp, color = nc.material.onSurfaceVariant)
                            }
                        },
                        onClick = { onModeChange(idx); expanded = false },
                        leadingIcon = if (idx == selectedMode) {{ Icon(Icons.Default.Check, "选中", Modifier.size(16.dp), tint = nc.material.primary) }} else null,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
