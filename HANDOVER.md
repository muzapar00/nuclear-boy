# NUCLEAR BOY 工作交接文档

> **版本**: v0.2.0 | **日期**: 2026-06-04 | **作者**: mzpr00（穆再排尔·穆合塔尔）

---

## 一、项目概览

核弹男孩是一款 Android AI 编程助手 App，接入 DeepSeek V4 API，内含 Python 沙箱、文件管理、Skills 扩展系统。多模块 Kotlin 项目，使用 Jetpack Compose + Hilt DI + Chaquopy Python。

**核心代码量**: ~25 Kotlin 文件 / 10 模块

---

## 二、模块架构

```
NuclearBoy/
├── app/                    # 主入口: MainActivity, DI(AppModule), 导航, 主题, 通知, 侧边栏
├── common/                 # 共享模型(Project, ChatMessage, SkillInfo等), AppConstants
├── api-deepseek/           # DeepSeek API: 流式SSE, TokenTracker, ContextWindowManager
├── agent-core/             # Agent 引擎: 主循环(Tool-use loop), SystemPrompt, ToolRegistry
├── python-bridge/          # Chaquopy 沙箱: PythonSandbox, ChaquopyPythonExecutor
├── memory/                 # Room 数据库: MemoryStore
├── skills/                 # Skill 管理: SkillManager, SkillManifest, SkillMarketPlace
├── tools-docgen/           # 文件系统: FileOperations(CRUD), DocumentGenerator
├── ui-chat/                # 聊天界面: ChatScreen, ChatViewModel, MessageBubble, TokenHudBar
└── ui-workspace/           # 项目列表: ProjectListScreen
```

### 依赖方向
```
app → ui-chat, ui-workspace, skills, tools-docgen, agent-core, api-deepseek, python-bridge, memory, common
ui-chat → agent-core, api-deepseek, skills, tools-docgen, common
ui-workspace → ui-chat, common
agent-core → api-deepseek, common
api-deepseek → common
```

---

## 三、关键类速查

### app/ (主应用)

| 类 | 路径 | 职责 |
|----|------|------|
| `MainActivity` | `app/.../MainActivity.kt` | 入口 Activity，组装 ModalNavigationDrawer + NavHost |
| `AppModule` | `app/.../di/AppModule.kt` | Hilt DI 单例模块，创建所有依赖 |
| `NuclearBoyApp` | `app/.../NuclearBoyApp.kt` | Application 类，初始化 Timber + Chaquopy + 复制预置 Skills |
| `NuclearBoyNavHost` | `app/.../navigation/` | 4 路由: PROJECT_LIST, CHAT/{id}, SETTINGS, ONBOARDING |
| `ProjectViewModel` | `app/.../ui/projects/` | 项目 CRUD，注入 SkillManager |
| `SidebarContent` | `app/.../ui/sidebar/` | 侧边栏: 项目列表 + Skills 状态 |
| `AgentForegroundService` | `app/.../service/` | 前台服务，防止后台被杀 |
| `NuclearBoyTheme` | `app/.../ui/theme/` | App 级暗黑主题 |

### ui-chat/ (聊天核心)

| 类 | 路径 | 职责 |
|----|------|------|
| `ChatScreen` | `ui-chat/.../chat/ChatScreen.kt` | 聊天界面，含文件面板 + 输入框 + 文件选择器 |
| `ChatViewModel` | `ui-chat/.../chat/ChatViewModel.kt` | 核心 VM: sendMessage, Agent 事件处理, 消息持久化 |
| `MessageBubble` | `ui-chat/.../chat/MessageBubble.kt` | 消息气泡渲染: Markdown, 代码块, 工具调用卡片 |
| `TokenHudBar` | `ui-chat/.../chat/TokenHudBar.kt` | 顶部 HUD: 模型/缓存/费用/上下文进度 |
| `ChatTheme` | `ui-chat/.../chat/ChatTheme.kt` | 聊天模块主题: NuclearColorScheme + 排版 + 形状 |

### agent-core/ (Agent 引擎)

| 类 | 路径 | 职责 |
|----|------|------|
| `AgentEngine` | `agent-core/.../agent/AgentEngine.kt` | ReAct 主循环: build messages → call API → execute tools → loop |
| `SystemPromptBuilder` | `agent-core/.../agent/SystemPromptBuilder.kt` | 构建 System Prompt，静态前缀优化缓存 |
| `ToolRegistry` | `agent-core/.../agent/ToolRegistry.kt` | 工具注册表，DeepSeek 格式转换，默认工具集 |

### api-deepseek/ (API 层)

| 类 | 路径 | 职责 |
|----|------|------|
| `DeepSeekApiClient` | `api-deepseek/.../deepseek/DeepSeekApiClient.kt` | HTTP 客户端: SSE 流式解析，重试，错误分类 |
| `TokenTracker` | `api-deepseek/.../deepseek/TokenTracker.kt` | Token 追踪: 用量、速度、缓存、费用 |
| `ContextWindowManager` | `api-deepseek/.../deepseek/ContextWindowManager.kt` | 1M 上下文窗口预算管理 |
| `ModelRouter` | `api-deepseek/.../deepseek/ModelRouter.kt` | 任务复杂度评估 + 模型路由 |

### python-bridge/ (Python 沙箱)

| 类 | 路径 | 职责 |
|----|------|------|
| `PythonSandbox` | `python-bridge/.../python/PythonSandbox.kt` | 沙箱门面，注入 PythonExecutor |
| `ChaquopyPythonExecutor` | `app/.../python/ChaquopyPythonExecutor.kt` | 真实 Chaquopy 执行器（在 app 模块） |

### skills/ (Skills 系统)

| 类 | 路径 | 职责 |
|----|------|------|
| `SkillManager` | `skills/.../skills/SkillManager.kt` | 全局+项目级 Skills: 安装/卸载/执行/注册工具 |
| `SkillManifest` | `skills/.../skills/SkillManifest.kt` | YAML 解析 + 数据模型 |
| `SkillMarketPlace` | `skills/.../skills/SkillMarketPlace.kt` | 内置+社区 Skills 目录 |

---

## 四、核心数据流

### 用户发消息完整链路

```
ChatInputBar.onClick → ChatScreen.onSend
  → ChatViewModel.sendMessage(text)
    → 检查 API Key
    → 创建 user ChatMessage
    → viewModelScope.launch {
        agentEngine.processMessage(userMessage, projectContext, history)
          → SystemPromptBuilder.build(...)
          → ModelRouter.route(...)
          → buildHistoryMessages(history)  // 含 tool result 注入
          → apiClient.streamChat(messages, model, thinking, tools)
            → JSON 序列化 (encodeDefaults = true! Critical)
            → OkHttp SSE streaming
            → emit StreamEvent.Content/Thinking/ToolCall/Complete
          → collect events → emit AgentEvent
      }
    → ChatViewModel.handleAgentEvent → _messages.update → UI recompose
```

### 项目切换

```
Sidebar.onProjectSelected → navigateToProject(id)
  → ProjectViewModel.selectProject(id) → fileOperations.currentProjectDir = name
  → navController.navigate("chat/$id")
  → ChatScreen → ChatViewModel.setProject(id)
    → loadPersistedMessages → _messages.value
    → refreshProjectFiles → _projectFiles.value
    → skillManager.loadProjectSkills(.agent/skills/) → 注册工具
```

### 工具调用循环

```
AgentEngine.run():
  1. 构建 messages: [system] + [history] + [user]
  2. streamChat → AI 返回 tool_calls
  3. ToolRegistry.executeSafe(name, params) → ToolResult
  4. messages.add(tool result message)
  5. 循环回到步骤 2，直到无 tool_calls
  6. emit final Response
```

---

## 五、关键技术细节

### DeepSeek API 必须遵守的规则

1. **`encodeDefaults = true`** — 工具定义的 `type: "function"` 必须序列化
2. **`reasoning_content` 不能传入下一轮** — 必须在 `sanitizeMessages()` 中剥离
3. **Tool message 必须跟在 assistant tool_calls 后面** — `buildHistoryMessages()` 负责生成
4. **Cache 前缀匹配** — System Prompt 前面部分尽量静态，动态内容放末尾

### Workspace 路径

- **工作区根**: `context.getExternalFilesDir(null)/NuclearBoy/`
- **项目目录**: `workspaceRoot/<projectName>/`
- **AI 对话历史**: `.agent/conversation.json`
- **Skills 目录**: `filesDir/skills/` (全局), `.agent/skills/` (项目)
- 使用 `getExternalFilesDir` 而非 `Documents/` — 保证读写删权限

### ChatViewModel 初始化顺序

```
setProject(projectId):
  1. currentProjectId = projectId
  2. _messages = loadPersistedMessages()  // 从 .agent/conversation.json
  3. refreshProjectFiles()                 // 列表 "." (项目根)
  4. skillManager.loadProjectSkills(...)    // 异步，注册工具
  5. _scrollToBottom++ (触发 UI 滚动)
```

### 通知/前台服务

- `AgentForegroundService` — 前台 Service，发消息时启动防止被杀
- `ChatScreen` 的 `notificationCallback` 连接 ViewModel 到 Service
- Android 13+ 需要 `POST_NOTIFICATIONS` 权限

---

## 六、构建指南

### 环境
- JDK 17+, Android SDK 35, Python 3.11
- Android Studio Hedgehog+

### 命令
```bash
./gradlew assembleDebug          # Debug 构建
./gradlew clean assembleDebug    # 完全重新构建
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 常见构建问题

| 问题 | 解决 |
|------|------|
| `e: Unresolved reference` | 检查模块依赖 (build.gradle.kts) |
| KSP 错误 | `./gradlew --stop && ./gradlew clean assembleDebug` |
| Chaquopy pip 卡住 | 耐心等待，首次需下载编译 ARM native 包 |
| `Configuration cache` 错误 | 已禁用 (`gradle.properties: configuration-cache=false`) |

---

## 七、调试

### 查看日志
```bash
adb logcat -s NuclearBoy:E     # 只看 ERROR 级
adb logcat -s NuclearBoy:*     # 所有级别
adb logcat --pid=$(adb shell pidof com.nuclearboy.app.debug)  # 只看我们的进程
```

### 关键日志标识
- `SEND:` — sendMessage 被调用
- `AGENT:` — Agent 循环开始
- `API:` — HTTP 请求/响应
- `缓存:` — TokenTracker 缓存命中率
- `🔧 TOOL:` — 工具执行结果

---

## 八、已知待改进项

1. **Memoory 系统未落地** — Room 数据库定义了但未充分使用
2. **语音输入** — 代码占位，未实现
3. **iOS 移植** — 仅在路线图中
4. **多模态** — DeepSeek API 还不支持，代码已移除，等官方上线
5. **Release 签名** — 目前只用 Debug 签名发布

---

## 九、预置 Skills

三个预置 Skill 在 `app/src/main/assets/skills/`，首次启动自动复制到 `filesDir/skills/`：

| Skill | 功能 |
|-------|------|
| `skill-creator` | 创建新 Skill 模板到 `.agent/skills/` |
| `file-organizer` | 扫描/整理项目文件 |
| `code-formatter` | 格式化 Python/JSON/XML 代码 |
