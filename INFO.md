# 核弹男孩 NUCLEAR BOY — v0.5.0

> **"名字很炸，性格很暖"** — 一款运行在 Android 上的 AI 编程助手

---

## v0.5.0 更新内容

### 新增功能

- **DeepSeek 模式切换**：左上角下拉菜单，聊天(Flash) / 思考(Pro+ThinkHigh) / 专家(Pro+ThinkMax) 三档手动切换
- **项目名称显示**：聊天页顶部居中绿底黑字胶囊标签
- **气泡全文复制按钮**：每条消息底部一键复制全文
- **气泡文本可选**：长按选中任意文本
- **Android 硬件桥接**：通过 Python `from java import jclass` 直接控制振动/手电筒/剪贴板/电池/传感器
- **Python 新预装包**：`requests`、`beautifulsoup4`

### 提示词工程

- **System Prompt 从 4000 字精简到 ~800 字**：纯正面示例，工具调用成功率从 ~50% 提升到 ~95%
- **API 级别修复 Flash 模式思考问题**：DeepSeek 默认思考=enabled，现明确传 `{"thinking": {"type": "disabled"}}`
- **完全关闭自动路由**：模型选择全权交给用户，不再自动判断
- **工具参数别名容错**：AI 写错参数名自动纠正（path/filePath/filename 互通）

### Bug 修复

| 类型 | 修复 |
|------|------|
| 🔴 | 中断工具调用后对话无法继续 — CoroutineScope 永久死亡，改为可重建 Job |
| 🔴 | 400 insufficient tool messages — 历史中未完成的 tool call 被过滤 |
| 🔴 | Flash 模式仍在思考 — DeepSeek 默认 enabled，须明确传 disabled |
| 🟡 | web_search 查询截断 — 改用 Python 端 urllib.parse.urlencode |
| 🟡 | web_search 英文搜索优化 — 自动检测语言切换 mkt |
| 🟡 | web_search 参数校验 — max_results=0 返回空，空 query 明确报错 |
| 🟡 | 项目名称显示 — 绿底黑字胶囊居中 |

---

## v0.4.0 更新内容

### 新增功能

- **Markdown 完整渲染**：表格、删除线、任务列表（Markwon 引擎）
- **代码块语法着色**：关键字/字符串/注释/函数名 VS Code 风格配色，可滚动
- **数学公式框**：`$$...$$` 渲染为专属公式样式
- **气泡全文复制按钮**：每条消息底部一键复制全文
- **气泡文本可选**：长按选中任意文本复制
- **Python 新预装包**：`requests`、`beautifulsoup4`

### 提示词工程（重大突破）

- **System Prompt 从 4000 字精简到 800 字**：砍掉所有无关内容
- **工具调用格式改版**：编号式"1. 调用 xxx，参数：xxx"格式，和 AI 自然输出一致
- **参数别名容错层**：AI 写错参数名自动纠正（如 path/filePath/filename 互通）
- **反污染清理**：移除全部否定表述、反面例子、不存在的功能引用
- **工作流规则精简**：从 50 行规则 → 4 行核心规则

### 优化改进

- **ModelRouter 重构**：短消息用 Flash+NonThink 秒回，复杂任务自动升级
- **文件面板重做**：右侧抽屉式，避开状态栏/输入栏，显眼关闭按钮
- **web_search 修复**：查询截断问题，改用 Python 端 `urllib.parse.urlencode` 编码
- **`generate_docx` 修复**：缺失 `from docx.oxml.ns import qn` 导入
- **`generate_xlsx` 实现**：实际调用 DocumentGenerator（之前是空壳）
- **项目删除修复**：`currentProjectDir` 双重嵌套路径问题
- **项目 ID 持久化**：UUID 方式，`.agent/project.json` 元数据
- **Python exec `__name__` 修复**：注入 `__main__`，`if __name__ == "__main__"` 生效
- **`output_path` 统一为 `path`**：所有文件工具参数名一致
- **开屏动画**：「新时代 新青年 新作为」渐显渐隐
- **设置页**：投喂作者（微信收款码）、API Key 图文教程
- **DeepSeek `number`→`integer`**：修复 API 不识别 `number` 类型

### Bug 修复

| 类型 | 修复 |
|------|------|
| 🔴 | ToolCallAccumulator 同一轮多工具调用参数丢失 |
| 🔴 | 400 Duplicate tool_call_id — 历史去重 + ToolExecution UPDATE |
| 🔴 | 400 reasoning_content must be passed back — 保留 reasoning |
| 🔴 | 400 InvalidRequest 不可重试 |
| 🔴 | 3 个不可用工具残留清理 |
| 🟡 | saveMessages 性能（每 token 写磁盘 → 仅完成时保存） |
| 🟡 | web_search SSL 恢复标准验证 |
| 🟡 | Chaquopy 注入风险修复 |
| 🟡 | Room 破坏性迁移移除 |

---

## v0.3.0 更新内容

### 新增功能

- **开屏动画**：「新时代 新青年 新作为」标语，渐显渐隐
- **API Key 教程页**：四步图文教程，手把手教用户获取 DeepSeek API Key
- **投喂作者**：设置页赞助入口，¥1 脆脆鲨 / ¥6 奶茶 / ¥15 午饭，微信扫码
- **文件面板重做**：右侧滑出抽屉式，目录导航 + 文件预览 + 分享
- **代码块复制按钮**：每个代码块顶部有 📋 一键复制
- **全项目调试日志系统**：`adb logcat -s NuclearBoy:E` 全程可追踪

### 提示词工程（核心突破）

- **工具描述专业化**：按"使用场景 + 参数示例 + 格式要求"四要素重写全部 10 个工具定义
- **参数名统一**：`output_path` → `path`，所有文件操作用同一个参数名
- **参数别名容错**：AI 写错参数名也不会失败（如 `url` 接受 `link`/`query`）
- **反污染清理**：移除所有否定表述、反面例子、不存在的功能引用
- **正面示例速查表**：每行一个可直接照抄的调用格式
- **ModelRouter 优化**：短消息不触发深度思考，首轮秒回

### Bug 修复（16 项）

| 类型 | 修复 |
|------|------|
| 🔴 严重 | ToolCallAccumulator 参数累积死代码 → 工具调用可正常执行 |
| 🔴 严重 | 400 Duplicate tool_call_id → 历史去重 |
| 🔴 严重 | 400 reasoning_content must be passed back → 保留 reasoning |
| 🔴 严重 | generate_docx 生成失败 → 添加缺失的 `from docx.oxml.ns import qn` |
| 🔴 严重 | generate_xlsx 空壳 → 实际调用 DocumentGenerator |
| 🔴 严重 | DeepSeek API `number` 类型不识别 → 全部改为 `integer` |
| 🟡 重要 | 项目删除失败（路径双重嵌套）→ 修复 currentProjectDir 处理 |
| 🟡 重要 | Python exec() 中 `__name__` 不为 `__main__` → 注入 `__name__` |
| 🟡 重要 | saveMessages 每个 token 都写磁盘 → 仅在完成时保存 |
| 🟡 重要 | web_search 禁用 SSL 验证 → 恢复标准 SSL |
| 🟡 重要 | Chaquopy 工作目录注入风险 → 安全转义 |
| 🟡 重要 | 项目 ID 冲突（目录名作 ID）→ UUID + 元数据持久化 |
| 🟢 次要 | search_content 返回假成功 → 改为明确错误 |
| 🟢 次要 | 3 个不可用工具残留（edit_file/execute_shell/share_file）→ 清理 |
| 🟢 次要 | Room 破坏性迁移 → 移除 fallbackToDestructiveMigration |
| 🟢 次要 | 死代码清理（executeStreamingRequest/AgentNotificationManager） |

---

## v0.2.0 更新内容

### 新增功能

- **侧边栏项目管理**：左滑或 hamburger 菜单呼出，项目列表 + 新建项目 + Skills 状态
- **首屏自动创建项目**：输入需求直接发送 → 自动生成项目名 → 创建项目 → 进入聊天
- **Skills 扩展系统**：全局 + 项目级 Skills，预置 skill-creator / file-organizer / code-formatter
- **Bing 联网搜索** (`web_search`)：Python 沙箱 + urllib 搜 Bing，国内可用
- **网页抓取** (`web_fetch`)：OkHttp 直连抓取网页文本内容
- **文件附件** (`📎`)：系统文件选择器 → 复制到项目目录
- **文件面板重写**：目录导航（进入子目录/返回上级）、文件分享（ACTION_SEND）、类型图标
- **`python-pptx` 预装**：直接生成 PPT，无需手写 XML
- **后台运行 + 通知**：前台 Service 保活，"正在思考" / "回复已就绪" 通知
- **应用图标**：绿色核芯 + `>` 终端符号，暗黑背景
- **正式工作交接文档** (`HANDOVER.md`)

### 优化改进

- System Prompt 完全静态化 → 缓存命中率从 20% 提升到 80%+
- 首轮对话自动用 Flash+NonThink 加速冷启动
- HUD 缓存命中率改为显示本次请求（非历史平均）
- 消息气泡 Markdown 渲染（粗体/斜体/代码/标题）
- 流式传输去掉逐字动画，直接显示 SSE 流
- 旧项目对话历史兼容（tool result 注入修复）
- 项目扫描修复（不再把子目录当项目）
- 删除项目可靠性修复
- 设置页返回键位置优化
- Android 13+ 通知权限处理

### 技术修复

- `encodeDefaults = true` — 工具定义 `type: "function"` 正确序列化
- `FunctionDefinitionDto.parameters: JsonObject` — 修复参数 JSON 序列化
- SSE 流式解析内联 — Content/Thinking/ToolCall/Complete 正确 emit
- 工作区路径改为 `getExternalFilesDir` — 完整读写删权限

---

---

## 基本信息

| 项目 | 详情 |
|------|------|
| **名称** | 核弹男孩 NUCLEAR BOY |
| **版本** | v0.5.0 |
| **作者** | mzpr00（穆再排尔·穆合塔尔） |
| **平台** | Android 8.0+ (API 26+) |
| **架构** | arm64-v8a（主流手机），x86_64（模拟器） |
| **AI 引擎** | DeepSeek V4 Pro / V4 Flash（1M 上下文窗口） |
| **语言** | 简体中文 |
| **许可证** | 私有项目 |

---

## DeepSeek API 深度适配

本 App 原生集成 DeepSeek API，是国内 AI 编程助手最佳选择：

### 模型矩阵

| 模型 | 参数 | 上下文 | 最大输出 | 思考模式 |
|------|------|--------|---------|---------|
| `deepseek-v4-pro` | 1.6T MoE | 1M tokens | 384K tokens | non-think / think-high / think-max |
| `deepseek-v4-flash` | 284B | 1M tokens | 384K tokens | non-think / think-high / think-max |

### 定价（V4 Pro，每百万 tokens）

| 计费项 | 价格 |
|--------|------|
| 输入（缓存未命中） | $0.435 |
| 输入（缓存命中） | $0.003625（**节省 99%**） |
| 输出（含思考 token） | $0.87 |

### 缓存优化

- DeepSeek 默认开启**上下文硬盘缓存**，前缀匹配自动命中
- 缓存命中仅 0.1 元/MTok（优惠 90%）
- **本 App 专为缓存优化**：静态 System Prompt 前置，动态内容后置
- 连续对话缓存命中率可达 **70-90%**
- 实时 HUD 显示命中率和费用

### 三种推理模式

| 模式 | 适用场景 |
|------|---------|
| **Non-Think** | 简单文件操作、快速问答、首轮对话 |
| **Think High** | 代码审查、Bug 修复、中等复杂度 |
| **Think Max** | 架构设计、复杂算法、多文件重构 |

- 智能路由：首条消息自动用 Flash+NonThink（冷缓存省钱）
- 后续复杂任务自动升级到 Pro+ThinkHigh
- 夜间自动降级 Flash 省费用

### API 关键规则

- `reasoning_content` **禁止**传入下一轮 messages（会返回 400）
- 发送前自动 `stripReasoningFromHistory()` 剥离
- System Prompt 前置以最大化缓存命中
- 流式 SSE 解析 + Token Delta 实时累加

### 实时可观测

- HUD 栏常驻：模型 · Token速度 · 缓存命中% · 费用
- 展开详情：输入/输出/思考 Token 分项统计
- 上下文占用进度条 + 黄/红预警

---

## 核心能力

### AI 编程助手
- 多轮对话，支持 DeepSeek 深度思考模式（Think High / Think Max）
- **1M token 上下文窗口**，长对话自动压缩摘要
- 智能模型路由：简单任务用 Flash 省钱，复杂任务用 Pro
- 实时 Token 用量 / 缓存命中率 / 费用显示
- **后台运行**：前台服务防止被杀，完成时推送通知

### Python 沙箱
- 嵌入式 CPython 3.11（Chaquopy），无需 root
- 预装库：`python-docx`、`openpyxl`、`Pillow`、`chardet`、`python-pptx`
- 直接执行 Python 脚本并捕获输出
- 安全沙箱隔离，文件操作限制在项目目录内

### 文件管理
- 创建/读写/搜索项目文件
- 文件面板支持**目录导航**（进入子目录/返回上级）
- **文件分享**：点击文件直接通过微信/QQ/邮件等发送
- 自动检测文件类型并匹配图标

### 文档生成
- Word 文档 (.docx) — 支持标题、段落、表格
- Excel 表格 (.xlsx) — 支持多 Sheet、数据、公式
- PowerPoint (.pptx) — 完整幻灯片生成
- 全部通过 Python 沙箱生成，无需联网

### 联网搜索
- Bing 搜索（国内可用），返回中文结果
- 网页抓取（`web_fetch`），直接读取网页文本
- web_search 工具：AI 可主动搜索互联网获取最新信息

### Skills 扩展系统
- 全局 Skills + 项目级 Skills
- 预置 3 个 Skills：**skill-creator**（创建新技能）、**file-organizer**（文件整理）、**code-formatter**（代码格式化）
- 编写 `skill.yaml` + `main.py` 即可扩展能力
- 自动注册为 AI 工具，支持参数验证

### 项目侧边栏
- 左滑或点击汉堡菜单呼出
- 项目列表、新建项目、Skills 状态一目了然
- 当前项目绿色高亮

### 智能上下文管理
- 缓存优化：静态前缀 + 动态内容分离，命中率 70-90%
- 自动压缩：上下文超限时 LLM 摘要旧对话
- 黄/红预警，实时上下文占用显示

---

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3（暗黑极客主题） |
| DI | Hilt (SingletonComponent) |
| 网络 | OkHttp 4 + 流式 SSE |
| 数据库 | Room (SQLite) |
| Python | Chaquopy 15（嵌入式 CPython 3.11） |
| AI | DeepSeek V4 API（OpenAI 兼容） |
| 序列化 | kotlinx.serialization |

---

## 项目结构

```
NuclearBoy/
├── app/                 # 主入口 + DI + 导航 + 主题 + 通知
├── common/              # 共享数据模型、常量、工具扩展
├── api-deepseek/        # DeepSeek API 客户端（流式 SSE + 缓存追踪）
├── agent-core/          # Agent 引擎 + ToolRegistry + SystemPrompt
├── python-bridge/       # Chaquopy Python 沙箱
├── memory/              # Room 三层记忆系统
├── skills/              # Skill 管理 + 市场
├── tools-docgen/        # Word/Excel/PPT 文档生成 + 文件操作
├── ui-chat/             # 聊天界面（Compose）
└── ui-workspace/        # 项目列表
```

---

## 构建指南

### 环境要求
- Android Studio Hedgehog+
- JDK 17+
- Android SDK 35
- Python 3.11（用于 Chaquopy 编译）
- Gradle 8.9+

### 构建命令
```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

### 安装到设备
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 环境变量
```
ANDROID_HOME=/path/to/android/sdk
```

---

## 使用指南

### 首次使用

> ⚠️ **重要：本 App 不自带 API Key！** 你需要自己注册 DeepSeek 账号并获取 Key。

**三步配置：**

1. **注册并充值**
   - 打开 [platform.deepseek.com](https://platform.deepseek.com)，注册账号
   - 支持微信/支付宝，最低充值 ¥10 即可使用
   - 推荐首次充 ¥20-50，V4 Flash 极便宜，¥10 能用很久

2. **创建 API Key**
   - 进入「API Keys」页面 → 点击「创建新的 API Key」
   - 复制 Key（格式：`sk-v4-xxxxxxxx`）
   - ⚠️ **Key 只显示一次，请立即保存！**

3. **配置到 App**
   - 打开核弹男孩 → 点右上角齿轮图标进入设置
   - 粘贴 API Key → 保存
   - 回到首页即可开始使用

> 💡 **费用参考**：日常代码问答用 V4 Flash，约 ¥0.001/次；复杂分析用 V4 Pro，约 ¥0.01/次。普通用户月花费通常在 ¥5-15。

### 快速上手
- **新建项目**：输入需求描述，自动创建项目并进入聊天
- **执行代码**：让 AI 写 Python 代码，它会自动调用 `run_python` 执行
- **生成文档**："帮我生成一份本周工作总结的 Word 文档"
- **搜索信息**："搜索今天的科技新闻" → AI 会用 Bing 搜索
- **安装技能**："帮我创建一个叫 my-tool 的 skill"

### 快捷键
| 操作 | 方式 |
|------|------|
| 侧边栏 | 左滑 或 点 hamburger 菜单 |
| 文件面板 | 点 HUD 栏 `[N]` 或输入框左边文件夹图标 |
| 设置 | 顶部齿轮图标 |
| 取消生成 | 红色停止按钮（发送键位置） |

---

## 已知限制

- **网络依赖**：AI 功能需要 DeepSeek API 连接
- **Android 13+ 通知权限**：需手动在系统设置中开启
- **Skills 安装**：需通过 `skill-creator` 或手动复制文件
- **x86_64 模拟器**：Chaquopy Python 库支持有限
- **后台时间**：前台服务可保活但系统仍可能在极端情况下回收

---

## 路线图

- [ ] iOS 移植（Kotlin Multiplatform + SwiftUI）
- [ ] 语音输入
- [ ] 图片/文件附件上传
- [ ] 记忆系统完善（三层记忆落地）
- [ ] Skill 市场（在线浏览和安装）
- [ ] 自定义主题
- [ ] 桌面小组件

---

## 关于作者

**mzpr00（穆再排尔·穆合塔尔）**

一个热爱编程的创造者。NUCLEAR BOY 的核心理念是让 AI 编程不再局限于桌面 — 躺着、坐着、通勤路上，随时随地写代码。

> "名字很炸，但性格很暖。" ☢️💚
