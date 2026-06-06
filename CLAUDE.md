# NUCLEAR BOY (核弹男孩)

> **作者**: mzpr00 (穆再排尔·穆合塔尔)
> **定位**: 温暖、智能、人性化的移动端 AI 编程助手

## 项目结构

```
NuclearBoy/
├── app/                    # 主入口 + DI + 导航 + 主题
├── common/                 # 共享模型、常量、扩展
├── api-deepseek/           # DeepSeek API 客户端
├── agent-core/             # Agent 引擎 + 工具注册
├── python-bridge/          # Chaquopy Python 沙箱
├── memory/                 # Room 三层记忆系统
├── skills/                 # Skill 管理 + 市场
├── tools-docgen/           # Word/Excel 文档生成
├── ui-chat/                # 聊天界面 Compose
└── ui-workspace/           # 文件浏览器 + Diff
```

## 技术栈

- **UI**: Jetpack Compose + Material 3 (暖色主题 #FF8C42)
- **DI**: Hilt (SingletonComponent)
- **网络**: OkHttp 4 + 流式 SSE
- **数据库**: Room (SQLite)
- **Python**: Chaquopy 15 (嵌入式 CPython)
- **序列化**: kotlinx.serialization

## 构建

1. 安装 Android Studio + SDK 35
2. 设置 `ANDROID_HOME` 环境变量
3. `./gradlew assembleDebug`

## 代码规范

### 枚举命名
所有枚举值使用 `UPPER_CASE`：
- `MessageStatus.COMPLETE` ✅ (不是 `Complete`)
- `ToolCallStatus.RUNNING` ✅ (不是 `Running`)
- `ChangeType.CREATED` ✅ (不是 `Created`)

### 错误处理
- 所有可失败操作返回 `AppResult<T>`
- 用户可见错误使用 `AppError.humanMessage`
- UI 层错误用友好语气（不是技术术语）

### 类型定义
- 跨模块共享的类型在 `:common` 的 `Models.kt`
- 模块内部类型定义在各自模块

### DeepSeek API 关键规则
- **禁止**将 `reasoning_content` 传入下一轮 messages（会返回 400）
- System Prompt 放在最前面以最大化缓存命中
- 使用 `stripReasoningFromHistory()` 清理历史消息

### 人性化原则
- 永远不说"操作已成功"→ 说"搞定了 ✨"
- 错误时先共情 → 再说问题 → 最后给方案
- 凌晨 (22:00-06:00) 自动轻声模式
- Emoji 适度使用 (2-3个/条)
