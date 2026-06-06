package com.nuclearboy.app.ui.tutorial

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TutorialStep(
    val title: String,
    val description: String,
    val assetPath: String,
    val tip: String = "",
)

/**
 * DeepSeek API Key 获取教程 — 使用本地截图手把手教用户获取 API Key。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val steps = remember {
        listOf(
            TutorialStep(
                title = "第 1 步：打开开发者平台",
                description = "用浏览器打开 platform.deepseek.com，注册或登录账号。\n找到左侧菜单的「API Keys」选项并点击。",
                assetPath = "tutorial/deepseekAPI开放平台截图1（点击“API keys”）.jpg",
                tip = "💡 新用户注册即送 500 万 tokens 免费额度",
            ),
            TutorialStep(
                title = "第 2 步：创建 API Key",
                description = "点击「创建新的 API Key」按钮，进入创建页面。",
                assetPath = "tutorial/deepseekAPI开放平台截图2（点击“创建API key”）.jpg",
                tip = "",
            ),
            TutorialStep(
                title = "第 3 步：设置名称并保存密钥",
                description = "随便输入一个名称（如「核弹男孩」），点击创建。\n⚠️ 密钥只显示一次！创建后立即复制保存到 App 设置中。\n一旦关闭页面，密钥将永远无法找回。",
                assetPath = "tutorial/deepseekAPI开放平台截图3（随便输入一个API key的名称，密码才重要，记得一定要保存好密码，创建完成后密码安徽自动销毁）.jpg",
                tip = "🔐 密钥格式：sk-v4-xxxxxxxx，请妥善保管",
            ),
            TutorialStep(
                title = "第 4 步：充值（可选但推荐）",
                description = "点击「充值」进入充值页面。支持微信/支付宝，最低 ¥10。\n推荐首次充 ¥20-50，日常使用 V4 Flash 极便宜，¥10 能用很久。",
                assetPath = "tutorial/deepseekAPI开放平台截图4（充点钱）.jpg",
                tip = "💰 V4 Flash 仅 ¥0.14/百万输入 tokens，每次对话约 ¥0.001",
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔑 获取 API Key 教程", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00E676).copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📖", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("跟着下面四步，3 分钟搞定 API Key",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("配好后就能在手机上随时随地用 AI 写代码啦",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Steps
            steps.forEachIndexed { index, step ->
                StepCard(index + 1, step.title, step.description, step.assetPath, step.tip)
            }

            // Bottom CTA
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://platform.deepseek.com/")
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color(0xFF08090B)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("🚀 打开 DeepSeek 开发者平台", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("返回设置", color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepCard(num: Int, title: String, desc: String, assetPath: String, tip: String) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF00E676)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$num", fontWeight = FontWeight.Bold, color = Color(0xFF08090B), fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
            if (tip.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(tip, style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E676), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))

            // Load screenshot from assets
            val bmp = remember(assetPath) {
                try { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } }
                catch (e: Exception) { android.util.Log.e("NuclearBoy", "[Tutorial] 无法加载截图: $assetPath — ${e.message}"); null }
            }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = title,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1C2028)),
                    contentAlignment = Alignment.Center) {
                    Text("📷 截图加载中…", color = Color(0xFF555A66), fontSize = 14.sp)
                }
            }
        }
    }
}
