# ☢️ NUCLEAR BOY（核弹男孩）

> **"Jarvis for a new era — knows your phone better than you do"** · AI coding assistant running on Android

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)]()
[![Version](https://img.shields.io/badge/version-0.7.0-orange)](https://github.com/muzapar00/nuclear-boy/releases)

---

- [English](#english) · [中文](#中文)

---

## English

### What is NUCLEAR BOY?

NUCLEAR BOY is a **free, open-source** AI coding assistant for Android. Powered by DeepSeek V4 API and Chaquopy Python 3.11 sandbox, it lets you write code, search the web, control phone hardware, and manage project files — all from your phone.

### Features

- 🤖 **AI Chat & Coding** — DeepSeek V4 Pro/Flash · 1M context window · Think/Expert/Chat modes
- 🐍 **Python 3.11 Sandbox** — Embedded Chaquopy CPython · preloaded with python-docx, openpyxl, Pillow, chardet, python-pptx, requests, beautifulsoup4
- 📱 **Hardware Control** — Control vibrator, flashlight, clipboard, battery, sensors, WiFi, Bluetooth, notifications, alarms, calendar, wallpaper, screen brightness, and more — via `run_python` + Chaquopy Java bridge
- 🌐 **Web Search** — Bing + Baidu dual-engine, auto-selects engine by CJK detection, ~700ms average
- 📁 **Project Management** — Multi-project · file browser · create/read/write/search files
- 🎨 **Markdown Rendering** — Tables, strikethrough, task lists, syntax-highlighted code blocks
- 🧩 **Skills Extensions** — Install custom skills, unlimited extensibility
- 📡 **Auto Update** — Checks your server + GitHub Releases, in-app APK download & install

### Quick Start

**Requirements:** Android 8.0+ (API 26+) · [DeepSeek API Key](https://platform.deepseek.com)

**Install:** Download the latest APK from [Releases](https://github.com/muzapar00/nuclear-boy/releases).

**Build from source:**
```bash
# Android Studio + SDK 35 + JDK 17
./gradlew assembleDebug
```

### Project Structure

```
NuclearBoy/
├── app/            # Main entry, DI, navigation, theme, services
├── common/         # Shared models, constants, extensions
├── api-deepseek/   # DeepSeek API client (SSE streaming)
├── agent-core/     # Agent engine, tool registry, system prompt
├── python-bridge/  # Chaquopy Python sandbox interface
├── memory/         # Room-based 3-layer memory system
├── skills/         # Skill manager & marketplace
├── tools-docgen/   # File operations & document generation
├── ui-chat/        # Chat UI (Jetpack Compose)
└── ui-workspace/   # Project list & workspace
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 (dark theme) |
| DI | Hilt (SingletonComponent) |
| Network | OkHttp 4 + SSE streaming |
| Database | Room (SQLite) |
| Python | Chaquopy 15 (CPython 3.11) |
| AI | DeepSeek V4 API |
| Serialization | kotlinx.serialization |

### Author

**mzpr00 (穆再排尔·穆合塔尔)**

- Blog: [muzapar.hongxinjie.cn](https://muzapar.hongxinjie.cn)
- Email: mapr00@163.com

### License

MIT License — see [LICENSE](LICENSE)

---

## 中文

> **"新时代的贾维斯，比你更懂你的手机"**

### 这是什么？

核弹男孩是一款 **免费、开源** 的 Android AI 编程助手。接入 DeepSeek V4 API，内置 Chaquopy Python 3.11 沙箱，让你在手机上写代码、搜资料、控制手机硬件、管理项目文件。

### 功能

- 🤖 **AI 对话编程** — DeepSeek V4 Pro/Flash · 1M 上下文 · 聊天/思考/专家三种模式
- 🐍 **Python 3.11 沙箱** — 嵌入式 Chaquopy CPython · 预装 python-docx、openpyxl、Pillow、chardet、python-pptx、requests、beautifulsoup4
- 📱 **硬件控制** — 振动、闪光灯、剪贴板、电池、传感器、WiFi、蓝牙、通知、闹钟、日历、壁纸、屏幕亮度……通过 `run_python` + Chaquopy Java 桥接全部可控
- 🌐 **联网搜索** — Bing + 百度双引擎 · 中文自动优先百度 · 平均 700ms 响应
- 📁 **项目管理** — 多项目切换 · 文件浏览 · 创建/读写/搜索文件
- 🎨 **Markdown 渲染** — 表格、删除线、任务列表、代码语法着色
- 🧩 **Skills 扩展** — 安装自定义 Skill，无限扩展
- 📡 **自动更新** — 检查作者服务器 + GitHub Releases · 应用内下载安装

### 快速开始

**环境要求：** Android 8.0+ (API 26+) · [DeepSeek API Key](https://platform.deepseek.com)

**安装：** 从 [Releases](https://github.com/muzapar00/nuclear-boy/releases) 下载最新 APK 直接安装。

**从源码构建：**
```bash
# 需要 Android Studio + SDK 35 + JDK 17
./gradlew assembleDebug
```

### 项目结构

```
NuclearBoy/
├── app/            # 主入口 · DI · 导航 · 主题 · 服务
├── common/         # 共享模型、常量、扩展
├── api-deepseek/   # DeepSeek API 客户端 (SSE 流式)
├── agent-core/     # Agent 引擎 · 工具注册 · 系统提示词
├── python-bridge/  # Chaquopy Python 沙箱接口
├── memory/         # Room 三层记忆系统
├── skills/         # Skill 管理 & 市场
├── tools-docgen/   # 文件操作 & 文档生成
├── ui-chat/        # 聊天界面 (Jetpack Compose)
└── ui-workspace/   # 项目列表 & 工作区
```

### 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 (暗黑主题) |
| DI | Hilt (SingletonComponent) |
| 网络 | OkHttp 4 + SSE 流式 |
| 数据库 | Room (SQLite) |
| Python | Chaquopy 15 (CPython 3.11) |
| AI | DeepSeek V4 API |
| 序列化 | kotlinx.serialization |

### 作者

**mzpr00（穆再排尔·穆合塔尔）**

- 博客：[muzapar.hongxinjie.cn](https://muzapar.hongxinjie.cn)
- 邮箱：mapr00@163.com

### 许可证

MIT License — 详见 [LICENSE](LICENSE)
