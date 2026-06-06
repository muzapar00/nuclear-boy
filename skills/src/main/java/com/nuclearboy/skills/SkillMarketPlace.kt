package com.nuclearboy.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A simple skill marketplace providing built-in skill manifests and
 * community skill search. In the future this can be backed by a remote
 * registry API; for now community skills are statically curated.
 */
class SkillMarketPlace {

    /**
     * Returns the manifests of all built-in (pre-packaged) skills.
     * These do not require network access and are always available.
     */
    /** Get all built-in skill manifests. */
    val builtInSkillsList: List<SkillManifest> get() = builtInSkills

    /**
     * Searches built-in + community skills by name or description.
     * A simple substring match; no fuzzy search yet.
     */
    fun searchCommunitySkills(query: String): List<SkillManifest> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return allCommunitySkills
        return allCommunitySkills.filter { skill ->
            skill.name.lowercase().contains(q) ||
                    skill.description.lowercase().contains(q) ||
                    skill.author.lowercase().contains(q)
        }
    }

    /**
     * Look up a skill by exact name match.
     */
    fun getSkillDetails(name: String): SkillManifest? {
        val trimmed = name.trim().lowercase()
        return allCommunitySkills.find {
            it.name.equals(trimmed, ignoreCase = true)
        }
    }

    /**
     * All known community skills (built-in + curated community list).
     * Lazy-initialized so built-ins are also returned by search.
     */
    private val allCommunitySkills: List<SkillManifest> by lazy {
        builtInSkills + communitySkills
    }

    /**
     * Asynchronously fetches skills from a remote marketplace.
     * Currently returns an empty list; wire up to a registry API in the future.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchRemoteSkills(query: String): List<SkillManifest> =
        withContext(Dispatchers.IO) {
            // TODO: Connect to remote skill registry
            emptyList()
        }

    // ──────────────────────────────────────────────
    //  Built-in skill manifests
    // ──────────────────────────────────────────────

    private val builtInSkills: List<SkillManifest> by lazy {
        listOf(
            pdfGeneratorSkill,
            wordReportSkill,
            excelDashboardSkill,
            codeFormatterSkill,
            fileOrganizerSkill,
        )
    }

    // ──────────────────────────────────────────────
    //  Community skill manifests
    // ──────────────────────────────────────────────

    private val communitySkills: List<SkillManifest> by lazy {
        listOf(
            SkillManifest(
                name = "api-tester",
                version = "0.2.0",
                description = "测试 REST API 端点并生成测试报告",
                author = "community",
                homepage = "https://github.com/nuclearboy/skill-api-tester",
                permissions = SkillPermissions(
                    network = NetworkPermission(allowed = true),
                ),
                parameters = listOf(
                    SkillParameter("url", "string", "API endpoint URL", required = true),
                    SkillParameter("method", "choice", "HTTP method (GET/POST/PUT/DELETE)", required = false, default = "GET"),
                    SkillParameter("body", "string", "Request body (JSON)", required = false),
                ),
            ),
            SkillManifest(
                name = "git-helper",
                version = "0.3.1",
                description = "生成 Git 提交信息、分支管理脚本和 release notes",
                author = "community",
                homepage = "https://github.com/nuclearboy/skill-git-helper",
                permissions = SkillPermissions(
                    shell = ShellPermission(allowed = true),
                    filesystem = FilesystemPermissions(
                        read = listOf("workspace/**", ".git/**"),
                        write = listOf("workspace/**"),
                    ),
                ),
                parameters = listOf(
                    SkillParameter("action", "choice", "Git 操作类型 (commit/log/diff/branch)", required = true),
                    SkillParameter("message", "string", "描述信息", required = false),
                ),
            ),
            SkillManifest(
                name = "json-yaml-converter",
                version = "0.1.0",
                description = "在 JSON 和 YAML 格式之间互相转换",
                author = "community",
                permissions = SkillPermissions(
                    filesystem = FilesystemPermissions(
                        read = listOf("workspace/**"),
                        write = listOf("workspace/**"),
                    ),
                ),
                parameters = listOf(
                    SkillParameter("input", "string", "输入文件路径", required = true),
                    SkillParameter("output", "string", "输出文件路径", required = true),
                    SkillParameter("format", "choice", "目标格式 (json/yaml)", required = true),
                ),
            ),
        )
    }

    // ──────────────────────────────────────────────
    //  Individual built-in skill definitions
    // ──────────────────────────────────────────────

    companion object {
        /**
         * pdf-generator: Converts Markdown to PDF with Chinese font support.
         */
        val pdfGeneratorSkill = SkillManifest(
            name = "pdf-generator",
            version = "1.0.0",
            description = "将 Markdown 文件转换为 PDF 文档，支持中文排版和代码高亮",
            author = "Nuclear Boy Team",
            homepage = "https://github.com/nuclearboy/skill-pdf-generator",
            permissions = SkillPermissions(
                filesystem = FilesystemPermissions(
                    read = listOf("workspace/**"),
                    write = listOf("workspace/**"),
                ),
                packages = PackagePermission(
                    allowed = listOf("markdown", "weasyprint", "pygments"),
                ),
            ),
            parameters = listOf(
                SkillParameter("input", "string", "输入的 Markdown 文件路径", required = true),
                SkillParameter("output", "string", "输出的 PDF 文件路径", required = true),
                SkillParameter("stylesheet", "string", "自定义 CSS 样式表路径", required = false),
                SkillParameter("paperSize", "choice", "纸张大小 (A4/Letter)", required = false, default = "A4"),
            ),
            entryPoint = "main:convert",
        )

        /**
         * word-report: Generates Word reports from markdown/templates.
         */
        val wordReportSkill = SkillManifest(
            name = "word-report",
            version = "1.1.0",
            description = "从模板或 Markdown 生成专业的 Word 报告文档",
            author = "Nuclear Boy Team",
            homepage = "https://github.com/nuclearboy/skill-word-report",
            permissions = SkillPermissions(
                filesystem = FilesystemPermissions(
                    read = listOf("workspace/**"),
                    write = listOf("workspace/**"),
                ),
                packages = PackagePermission(
                    allowed = listOf("python-docx", "jinja2"),
                ),
            ),
            parameters = listOf(
                SkillParameter("input", "string", "输入内容文件或 Markdown 路径", required = true),
                SkillParameter("output", "string", "输出的 .docx 文件路径", required = true),
                SkillParameter("template", "string", "Word 模板文件路径 (.docx)", required = false),
                SkillParameter("title", "string", "报告标题", required = false, default = "报告"),
            ),
            entryPoint = "main:generate",
        )

        /**
         * excel-dashboard: Creates Excel spreadsheets with data and charts.
         */
        val excelDashboardSkill = SkillManifest(
            name = "excel-dashboard",
            version = "1.0.0",
            description = "根据数据生成 Excel 电子表格，支持图表、格式化与多工作表",
            author = "Nuclear Boy Team",
            homepage = "https://github.com/nuclearboy/skill-excel-dashboard",
            permissions = SkillPermissions(
                filesystem = FilesystemPermissions(
                    read = listOf("workspace/**"),
                    write = listOf("workspace/**"),
                ),
                packages = PackagePermission(
                    allowed = listOf("openpyxl", "pandas"),
                ),
            ),
            parameters = listOf(
                SkillParameter("output", "string", "输出的 .xlsx 文件路径", required = true),
                SkillParameter("dataSource", "string", "数据源文件路径 (CSV/JSON)", required = true),
                SkillParameter("config", "string", "表格配置 JSON 文件路径", required = false),
                SkillParameter("chartType", "choice", "图表类型 (bar/line/pie/scatter)", required = false, default = "bar"),
            ),
            entryPoint = "main:build",
        )

        /**
         * code-formatter: Formats code files with standard styles.
         */
        val codeFormatterSkill = SkillManifest(
            name = "code-formatter",
            version = "1.2.0",
            description = "自动格式化代码文件，支持多种语言的缩进、换行和风格统一",
            author = "Nuclear Boy Team",
            homepage = "https://github.com/nuclearboy/skill-code-formatter",
            permissions = SkillPermissions(
                filesystem = FilesystemPermissions(
                    read = listOf("workspace/**"),
                    write = listOf("workspace/**"),
                ),
                packages = PackagePermission(
                    allowed = listOf("black", "autopep8"),
                ),
            ),
            parameters = listOf(
                SkillParameter("path", "string", "要格式化的文件或目录路径", required = true),
                SkillParameter("language", "choice", "编程语言 (python/kotlin/java/js)", required = false, default = "auto"),
                SkillParameter("indentSize", "int", "缩进空格数", required = false, default = "4"),
                SkillParameter("maxLineLength", "int", "最大行宽", required = false, default = "120"),
            ),
            entryPoint = "main:format",
        )

        /**
         * file-organizer: Organizes project files intelligently.
         */
        val fileOrganizerSkill = SkillManifest(
            name = "file-organizer",
            version = "1.0.0",
            description = "智能整理项目文件，按类型/日期/项目结构自动分类归档",
            author = "Nuclear Boy Team",
            homepage = "https://github.com/nuclearboy/skill-file-organizer",
            permissions = SkillPermissions(
                filesystem = FilesystemPermissions(
                    read = listOf("workspace/**"),
                    write = listOf("workspace/**"),
                ),
            ),
            parameters = listOf(
                SkillParameter("path", "string", "要整理的目标目录", required = true),
                SkillParameter("strategy", "choice", "整理策略 (by-type/by-date/by-project)", required = false, default = "by-type"),
                SkillParameter("dryRun", "bool", "预览模式（不实际移动文件）", required = false, default = "true"),
                SkillParameter("recursive", "bool", "是否递归处理子目录", required = false, default = "true"),
            ),
            entryPoint = "main:organize",
        )
    }
}
