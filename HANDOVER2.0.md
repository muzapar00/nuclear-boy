# NUCLEAR BOY 工作交接文档 v2.0

> **版本**: v0.5.0 | **日期**: 2025-06-06 | **作者**: mzpr00 / muzapar00

---

## 一、项目速览

核弹男孩 (NUCLEAR BOY) 是一款 Android AI 编程助手 App，接入 DeepSeek V4 API，内含 Python 沙箱、文件管理、Skills 扩展系统。多模块 Kotlin 项目，使用 Jetpack Compose + Hilt DI + Chaquopy Python。

| 属性 | 值 |
|------|-----|
| 包名 | `com.nuclearboy.app.debug` |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 35 |
| Kotlin | 2.0+ |
| Gradle | 8.11.1 |
| JDK | 17 |
| Python | 3.11 (Chaquopy 15) |
| AI 模型 | DeepSeek V4 Pro / V4 Flash |
| 许可证 | MIT |
| 仓库 | https://github.com/muzapar00/nuclear-boy |

---

## 二、模块架构

```
NuclearBoy/
├── app/                    # 主入口: MainActivity, DI(AppModule), 导航, 主题, 通知, 侧边栏
│   └── src/main/java/com/nuclearboy/app/
│       ├── MainActivity.kt           # Activity 入口，ModalNavigationDrawer 包裹整个 App
│       ├── NuclearBoyApp.kt          # Application 类，初始化 Timber/Chaquopy/复制预置 Skills
│       ├── di/AppModule.kt           # ⭐ Hilt DI 核心，所有依赖和工具的注册
│       ├── navigation/NuclearBoyNavHost.kt  # 5 路由: SPLASH → PROJECT_LIST / CHAT / SETTINGS / TUTORIAL
│       ├── python/ChaquopyPythonExecutor.kt # ⭐ Python 执行器，每次执行自动注入 sys.path + __main__
│       ├── service/AgentForegroundService.kt  # 前台服务，AI 思考时保活
│       ├── ui/splash/SplashScreen.kt    # "新时代 新青年 新作为" 开屏
│       ├── ui/projects/ProjectViewModel.kt   # ⭐ 项目 CRUD + UUID 元数据持久化
│       ├── ui/settings/SettingsScreen.kt     # 设置页（API Key/模型偏好/投喂作者）
│       ├── ui/sidebar/SidebarContent.kt      # 左侧抽屉：项目列表 + Skills 状态
│       └── ui/tutorial/TutorialScreen.kt     # API Key 获取图文教程
│
├── common/                 # 共享数据模型 (Models.kt)、AppConstants、AppError、AppResult、Extensions
│
├── api-deepseek/           # DeepSeek API 客户端
│   └── src/main/java/com/nuclearboy/api/deepseek/
│       ├── DeepSeekApiClient.kt       # ⭐ HTTP 客户端: SSE 流式解析, 重试, 错误分类, sanitizeMessages
│       ├── DeepSeekModels.kt          # DTO: ChatCompletionRequest, MessageDto, ToolDefinitionDto 等
│       ├── TokenTracker.kt            # Token 用量/缓存命中率/费用追踪
│       ├── ContextWindowManager.kt    # 1M 上下文窗口预算管理
│       ├── ModelRouter.kt             # 模型路由（已弃用自动路由，改为用户手动选择）
│       └── ApiKeyManager.kt           # API Key 存储/管理
│
├── agent-core/             # Agent 引擎
│   └── src/main/java/com/nuclearboy/agent/
│       ├── AgentEngine.kt             # ⭐⭐ 核心 ReAct 循环：build messages → API → tools → loop
│       ├── SystemPromptBuilder.kt     # ⭐ 系统提示词构建（精简 800 字，纯正面示例）
│       └── ToolRegistry.kt            # 工具注册表 + DeepSeek 格式转换 + executeSafe
│
├── python-bridge/          # Chaquopy Python 沙箱接口
│   └── PythonSandbox.kt, PolicyEnforcer.kt, PythonExecutor.kt
│
├── memory/                 # Room 数据库（三层记忆，尚未充分落地）
│
├── skills/                 # Skill 管理
│   └── SkillManager.kt               # ⭐ 全局+项目级 Skills: 安装/卸载/执行/注册工具 + YAML 解析
│
├── tools-docgen/           # 文件系统 + 文档生成
│   └── FileOperations.kt             # ⭐ 文件 CRUD，路径安全检查，项目创建
│       DocumentGenerator.kt          # Word/Excel 生成（Python 脚本 + python-docx/openpyxl）
│
├── ui-chat/                # 聊天界面
│   └── ChatScreen.kt                 # Scaffold + TokenHudBar + ChatInputBar + 右侧文件抽屉
│       ChatViewModel.kt              # ⭐⭐ 消息发送/持久化/Agent 事件处理
│       MessageBubble.kt              # Markwon 渲染 + 代码着色 + 复制按钮
│       TokenHudBar.kt                # HUD: 模型切换下拉 + 缓存命中率 + 费用
│       ChatTheme.kt                  # Matrix 黑客暗黑主题
│
└── ui-workspace/           # 项目列表
    └── ProjectListScreen.kt
```

### 依赖方向

```
app → ui-chat, ui-workspace, skills, tools-docgen, agent-core, api-deepseek, python-bridge, memory, common
ui-chat → agent-core, api-deepseek, skills, tools-docgen, common
agent-core → api-deepseek, common
api-deepseek → common
```

---

## 三、核心数据流

### 3.1 用户发消息完整链路

```
ChatInputBar.onClick
  → ChatScreen.onSend(text)
    → ChatViewModel.sendMessage(text)
      → 检查 API Key
      → 取消当前 agentJob（如果有）
      → 创建 USER ChatMessage → append to _messages
      → 创建 ASSISTANT 占位 ChatMessage（status=THINKING）
      → 构建 ProjectContext（project + files + skills）
      → 触发 notificationCallback("thinking")
      → 启动协程 on Dispatchers.IO:
          agentEngine.processMessage(
            userMessage = text,
            projectContext = ...,
            conversationHistory = _messages.value (排除 SYSTEM 消息),
            userMode = selectedMode       // 0=Chat 1=Think 2=Expert
          ).collect { event → handleAgentEvent(event, assistantId) }
      → finally: finalizeProcessing(assistantId)
        → _isProcessing = false
        → 如果 assistant 内容为空 → "没能生成回复"
        → 否则标 COMPLETE
        → saveMessages()
        → notificationCallback(assistant content)  // "ready" 通知
```

### 3.2 AgentEngine.run() 内部流程

```
1. 构建 System Prompt (SystemPromptBuilder.build)
   → 800 字精简版：身份 + 工具速查 + 工作流 + 环境 + 动态内容

2. 模型路由（手动模式）
   userMode=0 → Flash + NonThink ("聊天")
   userMode=1 → Pro + ThinkHigh ("思考")
   userMode=2 → Pro + ThinkMax  ("专家")

3. 构建 messages 数组
   [0] system prompt
   [1..N] buildHistoryMessages(conversationHistory)  // 含 tool 消息
   [N+1] user message + fileContents

4. ReAct 循环 (max 20 轮):
   a. 检查上下文警告级别
   b. toDeepSeekToolDefinitions() → 获取工具定义
   c. callApiStreaming() → SSE 流式接收
      → Thinking → emit(AgentEvent.Thinking)
      → Content  → emit(AgentEvent.StreamContent)
      → ToolCallRequest → accumulator.clear() + feed(id+name+args)
      → ToolCallDelta    → accumulator.feed(args fragments)
      → Complete → tokenTracker.onRequestComplete()
   d. 检查 toolCallsDetected && accumulator.hasPartialCalls()
      → 对于每个完成的 tool call:
        → executeSafe(name, params) → ToolResult
        → emit ToolExecution(RUNNING) → emit ToolExecution(COMPLETED/FAILED)
        → 将 tool result message 加入 messages 列表
        → continue 循环
   e. 无 tool call → 最终回复 → emit Response → emit Complete

5. 错误处理
   CancellationException → UserCancelled
   InvalidRequest (400) → 不重试
   NetworkTimeout/ServerError → 重试最多 3 次
```

### 3.3 buildHistoryMessages 关键逻辑

```
遍历 history.reversed() (从最新到最旧)
  ├─ 跳过 MessageRole.TOOL (旧版格式)
  ├─ 跳过 MessageRole.SYSTEM
  ├─ 预算控制: BUDGET_CONVERSATION_HISTORY = 100,000 tokens
  └─ 遇到 ASSISTANT with toolCalls:
       ├─ 按 toolCallId 去重 (AgentEngine 发射两次 ToolExecution: RUNNING+COMPLETED)
       ├─ 筛选 completedCalls (output != null && toolCallId != null)
       ├─ 生成 tool 消息 (role="tool", toolCallId=..., name=...)
       └─ 生成 assistant 消息 (toolCalls 只包含 completedCalls)
           ⚠️ 如果 completedCalls 为空 → toolCalls=null（防止 API 400 insufficient tool messages）
```

### 3.4 工具调用 → 参数别名 → 执行

```
ToolRegistry.executeSafe(toolName, params)
  → tools[toolName]? → execute(name, params)
    → executor(params)  // AppModule 中定义的 lambda
      → params["path"] ?: params["filePath"] ?: params["filename"]  // 别名容错
      → 执行实际操作
      → 返回 ToolResult(success, output, error, fileChanges)
  → 错误增强: 失败时附加 required 参数名提示
```

---

## 四、工具定义速查（10 个可用工具）

| # | 工具名 | 参数 | 别名 | 需确认 | 实现位置 |
|---|--------|------|------|--------|---------|
| 1 | `read_file` | `path` | filePath, filename | ❌ | AppModule.buildFileOperationTools |
| 2 | `write_file` | `path`, `content` | — | ✅ | AppModule.buildFileOperationTools |
| 3 | `list_directory` | `path` (可选) | — | ❌ | AppModule.buildFileOperationTools |
| 4 | `search_files` | `query` | — | ❌ | AppModule.buildFileOperationTools |
| 5 | `run_python` | `script` | — | ❌ | ToolRegistry.registerDefaultTools |
| 6 | `web_search` | `query`, `max_results` (可选) | — | ❌ | AppModule.buildWebTools |
| 7 | `web_fetch` | `url` | link, query | ❌ | AppModule.buildWebTools |
| 8 | `generate_docx` | `path`, `title`, `content` | output_path→path | ✅ | AppModule.buildDocumentTools |
| 9 | `generate_xlsx` | `path`, `sheet_data` | output_path→path | ✅ | AppModule.buildDocumentTools |
| 10 | `create_project` | `name` | path, projectName | ❌ | AppModule.buildFileOperationTools |

---

## 五、系统提示词设计（核心经验）

### 关键教训

1. **工具描述 (JSON Schema) 比系统提示词更重要**：模型做 tool call 时主要读工具定义的 `description` 字段，不是系统提示词
2. **绝对不要写否定表述**：提到"不要用 path"会植入错误模式（Ironic Process Theory）
3. **正面示例 > 规则列表**：`read_file(path="README.md")` 比 "read_file 需要 path 参数" 有效 10 倍
4. **精简至上**：4000 字提示词 → 模型注意力稀释 → 800 字后成功率从 50% 飙升到 95%
5. **DeepSeek 默认思考=enabled**：必须显式传 `{"thinking": {"type": "disabled"}}`

### 当前提示词结构（800 字）

```
你是核弹男孩，用中文回复。

1. 调用 read_file，参数：path="文件路径"
2. 调用 write_file，参数：path="文件路径"，content="内容"
...（10个工具，每行一个示例格式）

工作流程：先list_directory→再read_file→最后write_file
失败2次换方案。

Python 桥接模板：from java import jclass ...（控制振动/闪光灯/剪贴板）

环境：Android 15，Python 3.11，预装 python-docx/openpyxl/Pillow/chardet/python-pptx/requests/beautifulsoup4

[动态] 用户偏好 + 项目上下文 + Skills 列表
```

### 修改提示词时的检查清单

- [ ] 有没有否定表述（"不要"、"不能"、"禁止"）？
- [ ] 有没有 XX 那个工具不可用的标记？
- [ ] 每个工具行是不是纯正面示例？
- [ ] 总长度超过 1500 字了吗？
- [ ] 动态内容是不是放在最后？（缓存优化）

---

## 六、关键 Bug 和修复记录

### AgentEngine

| Bug | 现象 | 修复 |
|-----|------|------|
| ToolCallAccumulator 死代码 | `feed()` 从未被调用，工具无法执行 | `callApiStreaming` 中 `ToolCallDelta` 分支加 `accumulator.feed(event.deltas)` |
| 同一轮多工具参数丢失 | 第2个 ToolCallRequest 的 `clear()` 清掉第1个的 args | `clear()` 移到每轮 API 开头，不再每个 ToolCallRequest 清 |
| 400 Duplicate tool_call_id | 历史中同一 toolCallId 出现两次 | ChatViewModel: ToolExecution 改为 UPDATE 非 APPEND；buildHistoryMessages: 去重 |
| 400 reasoning_content must be passed back | DeepSeek 政策变更，思考模式下须回传 | sanitizeMessages 改为 no-op，保留 reasoningContent |
| 400 insufficient tool messages | 中断对话后 assistant 有未完成的 tool_calls | buildHistoryMessages: toolCalls 只包含 completedCalls |
| CoroutineScope 永久死亡 | cancel() 后新请求 112ms 退出 | scopeJob 改为可重建：`scopeJob.cancel(); scopeJob = SupervisorJob()` |

### API 层

| Bug | 现象 | 修复 |
|-----|------|------|
| Flash 模式仍在思考 | DeepSeek 默认 thinking=enabled | 显式传 `ThinkingConfigDto(type="disabled")` 而非 null |
| `number` 类型不识别 | DeepSeek 不认 "number" 类型 | 全部改为 "integer" |
| web_search 查询截断 | Kotlin URLEncoder + Python .format() 冲突 | 改用 Python `urllib.parse.urlencode()` |
| web_search 英文结果差 | mkt 固定 zh-CN | ASCII>50% 时自动切换 mkt=en-US |

### UI 层

| Bug | 现象 | 修复 |
|-----|------|------|
| 项目删除失败 | currentProjectDir 导致路径双重嵌套 | deleteProject 前清空 currentProjectDir |
| 项目 ID 冲突 | 目录名作 ID | UUID + .agent/project.json 元数据持久化 |
| saveMessages 性能 | 每个 token chunk 写磁盘 | 只在 finalizeProcessing 时保存 |
| Gradle 增量编译不更新 UI | `--rerun-tasks` 不够 | `clean assembleDebug` 确保完整重建 |

---

## 七、Chaquopy Python 沙箱

### 执行机制

`ChaquopyPythonExecutor.run(script, workingDir, env)` 每次执行都会：

1. 在 script 外层包裹 try/except/finally 脚手架
2. 自动注入 `skills` 目录到 `sys.path`
3. 设置 `__name__ = '__main__'`（修复 `if __name__ == '__main__'` 不生效）
4. stdout/stderr 写入临时文件后读回
5. 工作目录空白时不执行 `os.chdir()`（修复 generate_docx 的 `os.chdir('')` 错误）

### 预装 Python 包

```
python-docx, openpyxl, Pillow, chardet, python-pptx, requests, beautifulsoup4
```

配置位置: `app/build.gradle.kts` → `chaquopy { pip { install(...) } }`

首次构建时 Chaquopy 会下载交叉编译的 ARM64 原生包。新增包后需要 `clean build`。

### 已知限制

- **不能运行时 pip install**：手机上无法编译原生扩展，只能预置 pure-Python 包
- **numpy/matplotlib 太大**：5-7MB 的 ARM 原生包下载很慢，不适合预装
- **Android 权限**：振动无需权限，闪光灯需 CAMERA 权限（需用户手动开启）

---

## 八、构建与调试

### 构建

```bash
# 环境: Android Studio Hedgehog+, JDK 17, Android SDK 35
./gradlew assembleDebug              # Debug 构建
./gradlew clean assembleDebug        # ⭐ 完全重建（UI 资源变更时必用）
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 调试日志

所有日志使用 `android.util.Log.e("NuclearBoy", "[Prefix] message")`。用 ERROR 级别确保 `adb logcat -s NuclearBoy:E` 抓到全部。

```bash
# 全部日志
adb logcat -s NuclearBoy:E

# 按模块过滤
adb logcat -s NuclearBoy:E | grep "\[AgentEngine\]"   # Agent 循环
adb logcat -s NuclearBoy:E | grep "\[ChatVM\]"        # 消息处理
adb logcat -s NuclearBoy:E | grep "\[ApiClient\]"     # API 调用
adb logcat -s NuclearBoy:E | grep "\[ToolReg\]"       # 工具执行
adb logcat -s NuclearBoy:E | grep "\[Accumulator\]"   # Tool call 累积器
```

### 常见构建问题

| 问题 | 解决 |
|------|------|
| `e: Unresolved reference` | 检查模块依赖和 import |
| KSP 错误 | `./gradlew --stop && ./gradlew clean assembleDebug` |
| Chaquopy pip 卡住 | 等，首次需下载编译 ARM native 包 |
| UI 改动不生效 | `clean assembleDebug` — 增量编译经常不更新资源 |
| 构建缓存问题 | 删 `.gradle/` 目录重来 |

---

## 九、Git 和 GitHub

### 仓库

- **地址**: https://github.com/muzapar00/nuclear-boy
- **许可证**: MIT

### .gitignore 已排除

- `build/`、`.gradle/`、`gradle-temp/`、`gradle-*.zip`
- `*.apk`、`*.aab`
- `.kotlin/`、`.idea/`
- `local.properties`

### 提交规范

```bash
git add -A
git commit -m "描述你的改动"
git push
```

⚠️ 永远不要在提交前 `rm -rf .git` — 那样会丢失 git config！

---

## 十、未来优化方向

| 优先级 | 方向 | 说明 |
|--------|------|------|
| 🔴 高 | 数学公式渲染 | 找可用的 LaTeX 库（曾尝试 `ext-tex` 和 `huarangmeng/latex`，坐标均不对） |
| 🔴 高 | 代码语法着色升级 | 曾尝试 `ext-prism4j`，Maven 坐标不存在 |
| 🟡 中 | Memory 系统落地 | Room 数据库定义了但未充分使用 |
| 🟡 中 | 工具调用确认机制 | `requiresConfirmation` 字段定义了但未实现确认流程 |
| 🟡 中 | 搜索质量 | Bing 中国 IP 下结果漂移严重，可试 DuckDuckGo |
| 🟢 低 | iOS 移植 | Kotlin Multiplatform |
| 🟢 低 | 语音输入 | 占位未实现 |
| 🟢 低 | 自定义主题 | 只有暗黑模式 |

---

## 十一、快速排障流程

### AI 说工具调用失败率高

1. 检查 SystemPromptBuilder.kt — 有没有否定表述？
2. 检查 AppModule.kt — 工具描述是否自文档化（参数名嵌入描述）？
3. 检查 ToolRegistry.kt — 参数类型是否 "integer" 而非 "number"？
4. 清除项目 `.agent/conversation.json`（腐败历史会导致 400 错误）

### API 返回 400 错误

1. 检查 `reasoning_content` 是否正确回传
2. 检查 tool_calls 和 tool 消息是否配对
3. 查看 `adb logcat -s NuclearBoy:E | grep "API: ERROR"`

### 构建后 UI 没变化

1. 用 `clean assembleDebug` 而非 `assembleDebug`
2. 如果还是不变，删 `.gradle/` 目录

### Python 脚本执行失败

1. 检查 Chaquopy 是否已初始化（`Python.isStarted()`）
2. 检查 workingDir 是否有效路径
3. 查看 `adb logcat -s NuclearBoy:E | grep "\[Chaquopy\]"`

---

> 📅 2025-06-06 · 核弹男孩 v0.5.0 · MIT License
> 联系: mzpr00@163.com · GitHub: https://github.com/muzapar00/nuclear-boy
