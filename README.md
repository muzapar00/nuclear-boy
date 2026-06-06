# ☢️ NUCLEAR BOY（核弹男孩）

> **"名字很炸，性格很暖"** — 运行在 Android 上的 AI 编程助手

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)]()

---

## 简介

NUCLEAR BOY 是一款 **免费、开源** 的 Android AI 编程助手。接入 DeepSeek V4 API，内置 Python 沙箱、文件管理、Skills 扩展系统，让你在手机上就能写代码、搜资料、生成文档、控制硬件。

## 功能

- 🤖 **AI 对话编程** — DeepSeek V4 Pro/Flash，支持深度思考模式，1M 上下文窗口
- 🐍 **Python 3.11 沙箱** — 嵌入式 Chaquopy，预装 python-docx / openpyxl / Pillow / requests 等
- 📄 **文档生成** — Word (.docx)、Excel (.xlsx) 一键生成
- 🌐 **联网搜索** — Bing 搜索 + 网页抓取
- 📁 **项目管理** — 多项目切换、文件浏览、附件上传
- 🧩 **Skills 扩展** — 安装自定义 Skill，无限扩展能力
- 📱 **硬件桥接** — 通过 Python Java 桥接控制振动/手电筒/剪贴板/传感器
- 🎨 **Markdown 渲染** — 表格、删除线、任务列表、代码语法着色

## 快速开始

### 环境要求
- Android 8.0+ (API 26+)
- DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com) 注册获取）

### 下载安装
从 [Releases](https://github.com/muzapar00/nuclear-boy/releases) 下载最新 APK，直接安装。

### 构建
```bash
# 需要 Android Studio + SDK 35 + JDK 17
./gradlew assembleDebug
```

## 项目结构

```
NuclearBoy/
├── app/            # 主入口 + DI + 导航 + 主题
├── common/         # 共享模型、常量
├── api-deepseek/   # DeepSeek API 客户端
├── agent-core/     # Agent 引擎 + 工具注册
├── python-bridge/  # Chaquopy Python 沙箱
├── memory/         # Room 三层记忆系统
├── skills/         # Skill 管理
├── tools-docgen/   # 文档生成 + 文件操作
├── ui-chat/        # 聊天界面 Compose
└── ui-workspace/   # 项目列表 + 工作区
```

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| 网络 | OkHttp 4 + SSE 流式 |
| 数据库 | Room (SQLite) |
| Python | Chaquopy 15 (CPython 3.11) |
| AI | DeepSeek V4 API |
| 序列化 | kotlinx.serialization |

## 作者

**mzpr00（穆再排尔·穆合塔尔）**

- 博客：[muzapr.hongxinjie.cn](https://muzapr.hongxinjie.cn)
- 邮箱：mapr00@163.com

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
