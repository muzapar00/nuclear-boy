package com.nuclearboy.ui.chat

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclearboy.api.deepseek.ContextBudget
import com.nuclearboy.api.deepseek.ContextWarningLevel
import com.nuclearboy.api.deepseek.TokenSnapshot
import androidx.compose.ui.res.painterResource
import com.nuclearboy.common.ModelTier
import com.nuclearboy.common.ThinkingMode
import com.nuclearboy.common.AppConstants

@Composable
fun TokenHudBar(
    tokenSnapshot: TokenSnapshot,
    contextBudget: ContextBudget,
    modelTier: ModelTier,
    thinkingMode: ThinkingMode,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    projectRoot: String = "",
    fileCount: Int = 0,
    skillCount: Int = 0,
    onRefreshFiles: () -> Unit = {},
    context: Context? = null,
    selectedMode: Int = 0,
    onModeChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bg = NuclearBoyTheme.colorScheme.tokenBarBackground
    val nc = NuclearBoyTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(bg)
            .clickable { onToggle() }
            .semantics { contentDescription = "用量面板" }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Compact bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Mode dropdown: 聊天 / 思考 / 专家 — full logo banner
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
                    // Logo — aspect-ratio width, don't change button height
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

            val cacheColor = when {
                tokenSnapshot.cacheHitRate > 0.7 -> Color(0xFF4CAF50)
                tokenSnapshot.cacheHitRate > 0.3 -> Color(0xFFFFC107)
                else -> Color(0xFFFF5252)
            }
            Text(
                text = "缓存命中率 ${(tokenSnapshot.cacheHitRate * 100).toInt()}%",
                fontSize = 11.sp, color = cacheColor,
            )
            Text(
                text = "费用 ${AppConstants.usdToCny(tokenSnapshot.estimatedCostUsd)}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Skills badge
            if (skillCount > 0) {
                Text(
                    text = "sk:$skillCount",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = Color(0xFF0A84FF),
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Context usage bar
        val usagePercent = contextBudget.usagePercent
        val barColor = when (contextBudget.warningLevel) {
            ContextWarningLevel.OK, ContextWarningLevel.GREEN -> Color(0xFF4CAF50)
            ContextWarningLevel.YELLOW -> Color(0xFFFFC107)
            ContextWarningLevel.RED -> Color(0xFFFF5252)
        }
        LinearProgressIndicator(
            progress = { usagePercent.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = barColor, trackColor = bg.darken(0.1f),
        )

        // Expanded details
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                DetailRow("输入 Token (本次)", formatNum(tokenSnapshot.promptTokensThisRequest))
                DetailRow("输入 Token (总计)", formatNum(tokenSnapshot.promptTokensTotal))
                DetailRow("输出 Token (本次)", formatNum(tokenSnapshot.completionTokensThisRequest))
                DetailRow("输出 Token (总计)", formatNum(tokenSnapshot.completionTokensTotal))
                DetailRow("缓存命中 (本次)", formatNum(tokenSnapshot.cachedTokensThisRequest))
                DetailRow("缓存命中 (总计)", formatNum(tokenSnapshot.cachedTokensTotal))
                DetailRow("思考 Token", formatNum(tokenSnapshot.reasoningTokensTotal))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                DetailRow("上下文已用", "${formatNum(contextBudget.totalUsed)} / ${formatNum(AppConstants.DEEPSEEK_CONTEXT_WINDOW)}")
                DetailRow("剩余空间", formatNum(contextBudget.remaining))
                DetailRow("使用率", "${(contextBudget.usagePercent * 100).toInt()}%")

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                DetailRow("速度", "${tokenSnapshot.tokensPerSecond.toInt()} tok/s")
                DetailRow("平均延迟", "${tokenSnapshot.averageLatencyMs}ms")
                DetailRow("请求次数", "${tokenSnapshot.requestCount}")

                val warningMsg = when (contextBudget.warningLevel) {
                    ContextWarningLevel.YELLOW -> "上下文空间有限，建议精简对话"
                    ContextWarningLevel.RED -> "上下文即将耗尽，正在自动压缩"
                    else -> null
                }
                warningMsg?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = if (contextBudget.warningLevel == ContextWarningLevel.RED)
                            Color(0xFFFF5252) else Color(0xFFFFC107),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatNum(n: Long): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}K"
    else -> "$n"
}

private fun Color.darken(factor: Float): Color = copy(
    red = (red * (1 - factor)).coerceIn(0f, 1f),
    green = (green * (1 - factor)).coerceIn(0f, 1f),
    blue = (blue * (1 - factor)).coerceIn(0f, 1f),
)
