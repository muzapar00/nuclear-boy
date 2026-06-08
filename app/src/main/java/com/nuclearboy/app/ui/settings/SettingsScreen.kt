package com.nuclearboy.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.nuclearboy.api.deepseek.ApiKeyManager
import com.nuclearboy.app.R
import com.nuclearboy.app.update.UpdateDownloader
import com.nuclearboy.app.update.UpdateManager
import com.nuclearboy.common.ModelTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val apiKeyManager: ApiKeyManager,
) : androidx.lifecycle.ViewModel() {

    val apiKeyState = apiKeyManager.state

    fun setApiKey(key: String) {
        viewModelScope.launch {
            apiKeyManager.setPrimaryKey(key)
            // Trigger balance check
            // apiClient.checkBalance(key) -- needs a client instance
        }
    }

    fun removeApiKey() {
        viewModelScope.launch { apiKeyManager.removeKey(isPrimary = true) }
    }

    fun setAutoSwitch(enabled: Boolean) { apiKeyManager.setAutoSwitch(enabled) }
    fun setSimpleTasksUseFlash(enabled: Boolean) { apiKeyManager.setSimpleTasksUseFlash(enabled) }

    fun testConnection() {
        viewModelScope.launch {
            val key = apiKeyManager.getActiveKey() ?: return@launch
            // Validate by making a lightweight API call
            apiKeyManager.updateBalance("测试中…")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    onNavigateToTutorial: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val apiKeyState by viewModel.apiKeyState.collectAsState()
    val scrollState = rememberScrollState()
    var showSponsorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) },
                actions = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── API Key Section ──────────────────────────
            Text("🔑 API 设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // API Key status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (apiKeyState.hasPrimaryKey) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (apiKeyState.hasPrimaryKey) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (apiKeyState.hasPrimaryKey) "已配置: ${apiKeyState.primaryKeyMasked}"
                                   else "未配置 API Key",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (apiKeyState.balanceCny != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("💰 余额: ${apiKeyState.balanceCny}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(12.dp))

                    // API Key input
                    var apiKeyInput by remember { mutableStateOf("") }
                    var showKeyInput by remember { mutableStateOf(!apiKeyState.hasPrimaryKey) }

                    if (showKeyInput || !apiKeyState.hasPrimaryKey) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("DeepSeek API Key (sk-v4-...)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (apiKeyInput.isNotBlank()) {
                                    viewModel.setApiKey(apiKeyInput.trim())
                                    apiKeyInput = ""
                                    showKeyInput = false
                                }
                            }),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    viewModel.setApiKey(apiKeyInput.trim())
                                    apiKeyInput = ""
                                    showKeyInput = false
                                }
                            }, enabled = apiKeyInput.isNotBlank()) {
                                Text("保存")
                            }
                            if (apiKeyState.hasPrimaryKey) {
                                OutlinedButton(onClick = { showKeyInput = false }) {
                                    Text("取消")
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showKeyInput = true }) { Text("更换 Key") }
                            OutlinedButton(onClick = { viewModel.removeApiKey() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Text("删除 Key")
                            }
                        }
                    }
                }
            }

            // ── Model Section ────────────────────────────
            Text("🧠 模型偏好",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("默认模型: ${ModelTier.V4_PRO.displayName}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("说明: 简单任务自动用 ${ModelTier.V4_FLASH.displayName} 省钱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(8.dp))
                    var autoSwitch by remember { mutableStateOf(apiKeyState.autoSwitchEnabled) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoSwitch, onCheckedChange = {
                            autoSwitch = it; viewModel.setAutoSwitch(it)
                        })
                        Text("自动选择模型", style = MaterialTheme.typography.bodyMedium)
                    }
                    var simpleFlash by remember { mutableStateOf(apiKeyState.simpleTasksUseFlash) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = simpleFlash, onCheckedChange = {
                            simpleFlash = it; viewModel.setSimpleTasksUseFlash(it)
                        })
                        Text("简单任务用 Flash", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ── API Key Tutorial ─────────────────────────
            Text("📖 API Key 帮助",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("不知道怎么获取 API Key？",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("我们准备了一份详细的图文教程，手把手教你注册和获取 DeepSeek API Key。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToTutorial,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color(0xFF08090B)),
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("查看图文教程", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Sponsor Section ──────────────────────────
            Text("❤️ 投喂作者",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☢️ 核弹男孩 · 为爱发电",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "我是 mzpr00，一名普通的开发者。\n这款 App 永久免费开源，为所有同学打造。\n如果它帮到了你，可以请我喝杯奶茶 ☕",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    Spacer(Modifier.height(16.dp))

                    // Payment tier buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SponsorTierButton(
                            emoji = "🍫",
                            label = "一块脆脆鲨",
                            amount = "¥1",
                            modifier = Modifier.weight(1f),
                            onClick = { showSponsorDialog = true },
                        )
                        SponsorTierButton(
                            emoji = "🧋",
                            label = "一杯奶茶",
                            amount = "¥6",
                            modifier = Modifier.weight(1f),
                            onClick = { showSponsorDialog = true },
                        )
                        SponsorTierButton(
                            emoji = "🍜",
                            label = "一顿午饭",
                            amount = "¥15",
                            modifier = Modifier.weight(1f),
                            onClick = { showSponsorDialog = true },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "每一份支持都是我继续开发的动力 💪",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── Sponsor Dialog ────────────────────────────
            if (showSponsorDialog) {
                AlertDialog(
                    onDismissRequest = { showSponsorDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    title = {
                        Text("📱 扫码投喂",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            // 收款二维码
                            Image(
                                painter = painterResource(R.drawable.payment_qr),
                                contentDescription = "收款码",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "感谢每一位支持者！你的名字会被记录在 App 的致谢名单中 🌟",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSponsorDialog = false }) {
                            Text("先不了，下次一定", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }

            // ── About Section ────────────────────────────
            Text("ℹ️ 关于",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("☢️ 核弹男孩 NUCLEAR BOY", style = MaterialTheme.typography.titleMedium)

                    // 动态版本号
                    val currentVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                    } catch (e: Exception) { "0.0.0" }
                    Text("v$currentVersion · BUILD ${com.nuclearboy.app.BuildConfig.BUILD_TYPE.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(4.dp))
                    Text("作者: mzpr00 · mapr00@163.com", style = MaterialTheme.typography.bodySmall)
                    Text("免费开源 · 为所有同学打造", style = MaterialTheme.typography.bodySmall)
                    Text("新时代的贾维斯，比你更懂你的手机", style = MaterialTheme.typography.bodySmall)

                    Spacer(Modifier.height(12.dp))

                    // 检查更新按钮
                    var updateChecking by remember { mutableStateOf(false) }
                    var updateResult by remember { mutableStateOf<UpdateManager.UpdateResult?>(null) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                updateChecking = true
                                updateResult = null
                                kotlinx.coroutines.MainScope().launch {
                                    val um = UpdateManager(context)
                                    updateResult = um.checkForUpdate(force = true)
                                    updateChecking = false
                                }
                            },
                            enabled = !updateChecking,
                        ) {
                            if (updateChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("检查中...")
                            } else {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("检查更新")
                            }
                        }
                    }

                    // 更新结果
                    when (val result = updateResult) {
                        is UpdateManager.UpdateResult.Available -> {
                            Spacer(Modifier.height(8.dp))
                            Text("🆕 新版本 ${result.version} 可用！",
                                color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            if (result.body.isNotBlank()) {
                                Text(result.body.take(200),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                UpdateDownloader.download(context, result.url, result.version)
                            }) { Text("下载并安装 →", color = Color(0xFF00E676)) }
                        }
                        is UpdateManager.UpdateResult.UpToDate -> {
                            Spacer(Modifier.height(8.dp))
                            Text("✅ 已是最新版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is UpdateManager.UpdateResult.Error -> {
                            Spacer(Modifier.height(8.dp))
                            Text("⚠️ 检查失败: ${result.message}", color = MaterialTheme.colorScheme.error)
                        }
                        null -> {}
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SponsorTierButton(
    emoji: String,
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF00E676).copy(alpha = 0.06f),
            contentColor = Color(0xFF00E676),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 18.sp)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
        }
    }
}
