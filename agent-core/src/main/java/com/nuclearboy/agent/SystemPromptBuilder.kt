package com.nuclearboy.agent

import com.nuclearboy.common.*
import com.nuclearboy.common.SkillInfo
import java.time.LocalTime

object SystemPromptBuilder {

    fun build(
        userProfile: UserProfile,
        project: Project? = null,
        currentFiles: List<FileInfo> = emptyList(),
        activeSkills: List<SkillInfo> = emptyList(),
    ): String {
        android.util.Log.e("NuclearBoy", "[SysPrompt] build() entry hasProject=${project != null} fileCount=${currentFiles.size} skillCount=${activeSkills.size}")
        return buildString {
            // ═══════════════════════════════════════════════
            // IDENTITY (ultra-minimal)
            // ═══════════════════════════════════════════════
            appendLine("你是核弹男孩(NUCLEAR BOY)，运行在Android上的AI编程助手。用简体中文回复，像朋友一样说话。支持Markdown：表格、~~删除线~~、- [ ] 任务列表、代码块。")
            appendLine()

            // ═══════════════════════════════════════════════
            // TOOLS — THE ONLY SECTION THAT MATTERS
            // ═══════════════════════════════════════════════
            appendLine("你有以下工具，严格按格式调用：")
            appendLine()
            appendLine("1. 调用 read_file，参数：path=\"文件路径\"")
            appendLine("2. 调用 write_file，参数：path=\"文件路径\"，content=\"内容\"")
            appendLine("3. 调用 list_directory，参数：path=\"./\"")
            appendLine("4. 调用 search_files，参数：query=\"关键词\"")
            appendLine("5. 调用 run_python，参数：script=\"Python代码\"")
            appendLine("6. 调用 web_search，参数：query=\"搜索词\"")
            appendLine("7. 调用 web_fetch，参数：url=\"https://...\"")
            appendLine("8. 调用 generate_docx，参数：path=\"输出.docx\"，title=\"标题\"，content=\"Markdown内容\"")
            appendLine("9. 调用 generate_xlsx，参数：path=\"输出.xlsx\"，sheet_data='{\"sheets\":[{\"name\":\"Sheet1\",\"headers\":[],\"rows\":[]}]}'")
            appendLine("10. 调用 create_project，参数：name=\"项目名\"")
            appendLine()
            appendLine("你可以通过 run_python + Java 桥接控制手机硬件。模板如下（可直接复制修改）：")
            appendLine()
            appendLine("```python")
            appendLine("from java import jclass")
            appendLine("import time")
            appendLine("# 获取Context")
            appendLine("AT = jclass(\"android.app.ActivityThread\")")
            appendLine("ctx = AT.currentActivityThread().getApplication()")
            appendLine("C = jclass(\"android.content.Context\")")
            appendLine()
            appendLine("# 振动")
            appendLine("vib = ctx.getSystemService(C.VIBRATOR_SERVICE)")
            appendLine("VE = jclass(\"android.os.VibrationEffect\")")
            appendLine("vib.vibrate(VE.createOneShot(500, VE.DEFAULT_AMPLITUDE))")
            appendLine()
            appendLine("# 闪光灯 (需CAMERA权限)")
            appendLine("cm = ctx.getSystemService(C.CAMERA_SERVICE)")
            appendLine("cid = cm.getCameraIdList()[0]")
            appendLine("cm.setTorchMode(cid, True); time.sleep(0.3); cm.setTorchMode(cid, False)")
            appendLine()
            appendLine("# 剪贴板")
            appendLine("cb = ctx.getSystemService(C.CLIPBOARD_SERVICE)")
            appendLine("cb.setPrimaryClip(jclass(\"android.content.ClipData\").newPlainText(\"\", \"内容\"))")
            appendLine()
            appendLine("# 设备信息")
            appendLine("B = jclass(\"android.os.Build\")")
            appendLine("print(f\"{B.BRAND} {B.MODEL}, Android {jclass('android.os.Build\$VERSION').RELEASE}\")")
            appendLine("```")
            appendLine()

            // ═══════════════════════════════════════════════
            // WORKFLOW (3 rules only)
            // ═══════════════════════════════════════════════
            appendLine("工作流程：先list_directory看结构→再read_file读文件→最后write_file写文件。")
            appendLine("同一工具失败2次就换方案（如generate_docx失败改用run_python+python-docx）。")
            appendLine("如果参数总写错，就每次只发1个工具调用，确认成功再发下一个。")
            appendLine()

            // ═══════════════════════════════════════════════
            // CAPABILITIES (minimal)
            // ═══════════════════════════════════════════════
            appendLine("环境：Android ${android.os.Build.VERSION.RELEASE}，Python 3.11 (Chaquopy)，预装python-docx/openpyxl/Pillow/chardet/python-pptx。")
            appendLine("文件操作使用相对路径，默认在当前项目目录下。")
            appendLine()

            // ═══════════════════════════════════════════════
            // DYNAMIC SECTIONS
            // ═══════════════════════════════════════════════
            appendUserPreferences(userProfile)
            if (project != null) appendProjectContext(project, currentFiles)
            if (activeSkills.isNotEmpty()) appendActiveSkills(activeSkills)
        }
    }

    private fun StringBuilder.appendUserPreferences(profile: UserProfile) {
        if (profile.preferredLanguages.isEmpty() && profile.preferredFrameworks.isEmpty()) return
        append("用户偏好：")
        if (profile.preferredLanguages.isNotEmpty()) append("常用${profile.preferredLanguages.joinToString("/")} ")
        if (profile.preferredFrameworks.isNotEmpty()) append("框架${profile.preferredFrameworks.joinToString("/")} ")
        appendLine()
        appendLine()
    }

    private fun StringBuilder.appendProjectContext(project: Project, files: List<FileInfo>) {
        appendLine("当前项目：${project.name}")
        if (project.techStack.isNotEmpty()) appendLine("技术栈：${project.techStack.joinToString("/")}")
        if (files.isNotEmpty()) {
            val names = files.take(15).joinToString(" ") { f ->
                if (f.isDirectory) "${f.name}/" else f.name
            }
            appendLine("文件列表：$names")
            if (files.size > 15) appendLine("...还有${files.size - 15}个文件")
        }
        appendLine()
    }

    private fun StringBuilder.appendActiveSkills(skills: List<SkillInfo>) {
        appendLine("已激活Skills：${skills.joinToString { it.name }}")
        appendLine()
    }
}
