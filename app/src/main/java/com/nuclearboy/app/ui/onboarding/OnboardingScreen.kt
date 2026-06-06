package com.nuclearboy.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuclearboy.api.deepseek.ApiKeyManager
import kotlinx.coroutines.launch

/**
 * First-run onboarding — a warm, conversational welcome.
 *
 * Instead of a cold tutorial, NUCLEAR BOY introduces himself
 * like a friend and asks a few questions to get to know the user.
 */
@Composable
fun OnboardingScreen(
    apiKeyManager: ApiKeyManager,
    onComplete: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedLanguage by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Step indicator
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentStep) 12.dp else 8.dp)
                            .then(
                                if (i == currentStep) Modifier else Modifier
                            ),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (i <= currentStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() + slideInHorizontally { it } togetherWith fadeOut() + slideOutHorizontally { -it } },
            ) { step ->
                when (step) {
                    0 -> WelcomeStep(onNext = { currentStep = 1 })
                    1 -> LanguageQuestionStep(
                        selected = selectedLanguage,
                        onSelect = { selectedLanguage = it },
                        onNext = { currentStep = 2 },
                    )
                    2 -> ApiKeySetupStep(
                        apiKey = apiKeyInput,
                        onApiKeyChange = { apiKeyInput = it },
                        onSaved = { keySaved = true },
                        apiKeyManager = apiKeyManager,
                    )
                    3 -> ReadyStep(onComplete = onComplete)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Logo / icon
        Text(
            text = "☢️",
            fontSize = 64.sp,
        )

        Text(
            text = "NUCLEAR BOY",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "核弹男孩",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "嘿！我是核弹男孩。\n名字很炸对吧？但我其实挺暖的 😄",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "我是来帮你在手机上写代码的。\n躺着写、坐着写、趴着写都行。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("在开始之前，我想了解你一点点… →")
        }
    }
}

@Composable
private fun LanguageQuestionStep(
    selected: String,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
) {
    val languages = listOf(
        "Python" to "🐍",
        "JavaScript" to "📜",
        "Kotlin" to "💜",
        "Java" to "☕",
        "其他" to "🔧",
        "我啥都会" to "🌟",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "你平时主要用什么语言？",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        languages.forEach { (lang, emoji) ->
            val isSelected = selected == lang
            OutlinedButton(
                onClick = { onSelect(lang) },
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = if (isSelected) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text("$emoji  $lang")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "不着急，随便选，以后随时能改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onNext,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            Text("继续 →")
        }
    }
}

@Composable
private fun ApiKeySetupStep(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSaved: () -> Unit,
    apiKeyManager: ApiKeyManager,
) {
    var showError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "🔑", fontSize = 48.sp)
        Text("连接 DeepSeek AI", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "去 platform.deepseek.com 注册\n充值最低 ¥10，创建 Key（sk-v4-开头）\n粘贴到下面：",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { onApiKeyChange(it); showError = null },
            label = { Text("sk-v4-...") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            singleLine = true,
            isError = showError != null,
        )
        showError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSaved) { Text("稍后设置") }
            Button(
                onClick = {
                    if (!apiKey.startsWith("sk-")) {
                        showError = "Key 应以 sk- 开头"
                        return@Button
                    }
                    scope.launch {
                        apiKeyManager.setPrimaryKey(apiKey.trim())
                        onSaved()
                    }
                },
                enabled = apiKey.isNotBlank(),
            ) { Text("保存 →") }
        }
    }
}

@Composable
private fun ReadyStep(onComplete: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "🚀", fontSize = 64.sp)
        Text("一切就绪！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(
            "核弹男孩已经准备好了。\n创建你的第一个项目，开始写代码吧！",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("开始使用 →")
        }
    }
}
