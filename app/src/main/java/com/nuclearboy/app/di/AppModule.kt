package com.nuclearboy.app.di

import android.content.Context
import com.nuclearboy.agent.AgentEngine
import com.nuclearboy.agent.SystemPromptBuilder
import com.nuclearboy.agent.ToolRegistry
import com.nuclearboy.agent.ToolDefinition
import com.nuclearboy.agent.ToolParameter
import com.nuclearboy.agent.ToolResult
import com.nuclearboy.api.deepseek.*
import com.nuclearboy.common.*
import com.nuclearboy.app.python.ChaquopyPythonExecutor
import com.nuclearboy.memory.MemoryStore
import com.nuclearboy.python.PolicyEnforcer
import com.nuclearboy.python.PythonExecutor
import com.nuclearboy.python.PythonSandbox
import com.nuclearboy.skills.SkillManager
import com.nuclearboy.skills.SkillMarketPlace
import com.nuclearboy.tools.docgen.DocumentGenerator
import com.nuclearboy.tools.docgen.FileOperations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── DeepSeek API ──────────────────────────────────

    @Provides
    @Singleton
    fun provideApiKeyManager(@ApplicationContext context: Context): ApiKeyManager {
        android.util.Log.e("NuclearBoy", "[DI] provideApiKeyManager")
        return ApiKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideTokenTracker(): TokenTracker {
        android.util.Log.e("NuclearBoy", "[DI] provideTokenTracker")
        return TokenTracker()
    }

    @Provides
    @Singleton
    fun provideContextWindowManager(): ContextWindowManager {
        android.util.Log.e("NuclearBoy", "[DI] provideContextWindowManager")
        return ContextWindowManager()
    }

    @Provides
    @Singleton
    fun provideModelRouter(): ModelRouter {
        android.util.Log.e("NuclearBoy", "[DI] provideModelRouter")
        return ModelRouter()
    }

    @Provides
    @Singleton
    fun provideDeepSeekApiClient(
        apiKeyManager: ApiKeyManager,
        tokenTracker: TokenTracker,
        contextWindowManager: ContextWindowManager,
    ): DeepSeekApiClient {
        android.util.Log.e("NuclearBoy", "[DI] provideDeepSeekApiClient")
        return DeepSeekApiClient(
            apiKeyProvider = { apiKeyManager.getActiveKey() },
            tokenTracker = tokenTracker,
            contextManager = contextWindowManager,
        )
    }

    // ── Python Sandbox ────────────────────────────────

    @Provides
    @Singleton
    fun providePythonExecutor(): PythonExecutor {
        android.util.Log.e("NuclearBoy", "[DI] providePythonExecutor -> ChaquopyPythonExecutor")
        return ChaquopyPythonExecutor()
    }

    @Provides
    @Singleton
    fun providePythonSandbox(
        @ApplicationContext context: Context,
        executor: PythonExecutor,
    ): PythonSandbox {
        android.util.Log.e("NuclearBoy", "[DI] providePythonSandbox")
        val sandbox = PythonSandbox(context)
        sandbox.executor = executor
        return sandbox
    }

    @Provides
    @Singleton
    fun providePolicyEnforcer(): PolicyEnforcer {
        android.util.Log.e("NuclearBoy", "[DI] providePolicyEnforcer")
        return PolicyEnforcer()
    }

    // ── Memory ────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMemoryStore(@ApplicationContext context: Context): MemoryStore {
        android.util.Log.e("NuclearBoy", "[DI] provideMemoryStore")
        return MemoryStore(context)
    }

    // ── Skills ────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSkillsDir(@ApplicationContext context: Context): File {
        android.util.Log.e("NuclearBoy", "[DI] provideSkillsDir")
        return File(context.filesDir, "skills").also { it.mkdirs() }
    }

    @Provides
    @Singleton
    fun provideSkillManager(
        pythonSandbox: PythonSandbox,
        skillsDir: File,
    ): SkillManager {
        android.util.Log.e("NuclearBoy", "[DI] provideSkillManager")
        return SkillManager(pythonSandbox, skillsDir)
    }

    @Provides
    @Singleton
    fun provideSkillMarketPlace(): SkillMarketPlace {
        android.util.Log.e("NuclearBoy", "[DI] provideSkillMarketPlace")
        return SkillMarketPlace()
    }

    // ── Tools ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDocumentGenerator(pythonSandbox: PythonSandbox): DocumentGenerator {
        android.util.Log.e("NuclearBoy", "[DI] provideDocumentGenerator")
        return DocumentGenerator(pythonSandbox)
    }

    @Provides
    @Singleton
    fun provideFileOperations(@ApplicationContext context: Context): FileOperations {
        android.util.Log.e("NuclearBoy", "[DI] provideFileOperations")
        // Use app-specific external storage for full read/write/delete permissions
        val root = File(context.getExternalFilesDir(null), AppConstants.APP_DOCUMENTS_DIR).also { it.mkdirs() }
        return FileOperations(root)
    }

    // ── Agent ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideToolRegistry(
        pythonSandbox: PythonSandbox,
        documentGenerator: DocumentGenerator,
        fileOperations: FileOperations,
        skillManager: SkillManager,
    ): ToolRegistry {
        android.util.Log.e("NuclearBoy", "[DI] provideToolRegistry — entry")
        val registry = ToolRegistry()

        runBlocking {
            android.util.Log.e("NuclearBoy", "[DI] registerDefaultTools")
            registry.registerDefaultTools()

            val fileTools = buildFileOperationTools(fileOperations)
            android.util.Log.e("NuclearBoy", "[DI] buildFileOperationTools — ${fileTools.size} tools: ${fileTools.joinToString { it.name }}")
            registry.registerAll(fileTools)

            val docTools = buildDocumentTools(documentGenerator)
            android.util.Log.e("NuclearBoy", "[DI] buildDocumentTools — ${docTools.size} tools: ${docTools.joinToString { it.name }}")
            registry.registerAll(docTools)

            val webTools = buildWebTools(pythonSandbox)
            android.util.Log.e("NuclearBoy", "[DI] buildWebTools — ${webTools.size} tools: ${webTools.joinToString { it.name }}")
            registry.registerAll(webTools)

        }

        registry.pythonExecutor = { _, params ->
            val scriptCode = params["script"]
            android.util.Log.e("NuclearBoy", "[DI] pythonExecutor called — scriptLen=${scriptCode?.length ?: 0}, workingDir=${params["workingDir"]}, keys=${params.keys}")
            if (scriptCode == null) ToolResult(false, "", error = "缺少 script 参数")
            else {
                val wd = params["workingDir"]?.takeIf { it != "." } ?: fileOperations.projectRoot().absolutePath
                val r = pythonSandbox.execute(scriptCode, wd)
                android.util.Log.e("NuclearBoy", "[DI] pythonExecutor result — exitCode=${r.exitCode}, stdoutLen=${r.stdout.length}, stderrLen=${r.stderr.length}")
                ToolResult(success = r.exitCode == 0, output = r.stdout, error = r.stderr.ifBlank { null })
            }
        }

        registry.skillsExecutor = { name, params ->
            android.util.Log.e("NuclearBoy", "[DI] skillsExecutor called — skillName=$name, params=${params}")
            when (val r = skillManager.executeSkill(name, params)) {
                is AppResult.Success -> {
                    android.util.Log.e("NuclearBoy", "[DI] skillsExecutor — skill '$name' OK")
                    ToolResult(true, "OK")
                }
                is AppResult.Failure -> {
                    android.util.Log.e("NuclearBoy", "[DI] skillsExecutor — skill '$name' FAILED: ${r.error.humanMessage}")
                    ToolResult(false, "", error = r.error.humanMessage)
                }
            }
        }

        skillManager.onToolRegister = { name, desc, _ ->
            android.util.Log.e("NuclearBoy", "[DI] skill tool register callback — skillName=$name, desc=${desc.take(50)}")
            runBlocking { registry.register(ToolDefinition("skill_$name", desc,
                executor = { p ->
                    when (val r = runBlocking { skillManager.executeSkill(name, p) }) {
                        is AppResult.Success -> ToolResult(true, "OK")
                        is AppResult.Failure -> ToolResult(false, "", error = r.error.humanMessage)
                    }
                }))
            }
        }
        skillManager.onToolUnregister = { name ->
            android.util.Log.e("NuclearBoy", "[DI] skill tool unregister callback — skillName=$name")
            runBlocking { registry.unregister("skill_$name") }
        }

        android.util.Log.e("NuclearBoy", "[DI] provideToolRegistry — returning registry")
        return registry
    }

    @Provides
    @Singleton
    fun provideAgentEngine(
        apiClient: DeepSeekApiClient,
        toolRegistry: ToolRegistry,
        contextManager: ContextWindowManager,
        tokenTracker: TokenTracker,
        modelRouter: ModelRouter,
    ): AgentEngine {
        android.util.Log.e("NuclearBoy", "[DI] provideAgentEngine")
        return AgentEngine(
            apiClient = apiClient,
            toolRegistry = toolRegistry,
            contextManager = contextManager,
            tokenTracker = tokenTracker,
            modelRouter = modelRouter,
        )
    }

    // ── Tool Builder Helpers ──────────────────────────

    private fun buildFileOperationTools(fileOps: FileOperations): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "read_file",
                description = "读取项目中的文件内容。使用场景：1) 用户让你查看文件内容；2) 需要了解代码结构；3) 需要分析文件信息。参数 path 必须是具体文件路径，不能是目录。示例：path=\"README.md\"",
                parameters = listOf(
                    ToolParameter("path", "string", "文件路径。示例：README.md、src/main.py。不能传目录或 .", required = true),
                ),
                executor = { params ->
                    val path = params["path"] ?: params["filePath"] ?: params["filename"] ?: return@ToolDefinition ToolResult(false, error = "缺少 path 参数。如 path=\"README.md\"")
                    android.util.Log.e("NuclearBoy", "[DI] read_file — path=$path")
                    when (val result = kotlinx.coroutines.runBlocking { fileOps.readFile(path) }) {
                        is AppResult.Success -> {
                            val outLen = result.data.content?.length ?: 0
                            android.util.Log.e("NuclearBoy", "[DI] read_file SUCCESS — outputLen=$outLen")
                            ToolResult(
                                success = true,
                                output = result.data.content ?: "(空文件)",
                                fileChanges = listOf(
                                    FileChange(path, ChangeType.MODIFIED, null)
                                ),
                            )
                        }
                        is AppResult.Failure -> {
                            android.util.Log.e("NuclearBoy", "[DI] read_file FAILED — ${result.error.humanMessage}")
                            ToolResult(false, error = result.error.humanMessage)
                        }
                    }
                },
            ),
            ToolDefinition(
                name = "write_file",
                description = "创建新文件或覆盖已有文件。使用场景：1) 用户让你创建文件；2) 需要修改文件内容；3) 生成代码文件。会自动创建父目录。",
                parameters = listOf(
                    ToolParameter("path", "string", "文件路径。示例：hello.py、src/main.py", required = true),
                    ToolParameter("content", "string", "要写入的完整内容。示例：print('Hello World')", required = true),
                ),
                requiresConfirmation = true,
                executor = { params ->
                    val path = params["path"] ?: return@ToolDefinition ToolResult(false, error = "缺少 path 参数")
                    val content = params["content"] ?: return@ToolDefinition ToolResult(false, error = "缺少 content 参数")
                    android.util.Log.e("NuclearBoy", "[DI] write_file — path=$path, contentLen=${content.length}")
                    when (val result = kotlinx.coroutines.runBlocking { fileOps.writeFile(path, content) }) {
                        is AppResult.Success -> {
                            android.util.Log.e("NuclearBoy", "[DI] write_file SUCCESS — path=$path")
                            ToolResult(
                                success = true,
                                output = "文件已写入: $path",
                                fileChanges = listOf(FileChange(path, ChangeType.MODIFIED, null)),
                            )
                        }
                        is AppResult.Failure -> {
                            android.util.Log.e("NuclearBoy", "[DI] write_file FAILED — ${result.error.humanMessage}")
                            ToolResult(false, error = result.error.humanMessage)
                        }
                    }
                },
            ),
            ToolDefinition(
                name = "list_directory",
                description = "列出目录中的文件和子目录。使用场景：1) 需要了解项目结构；2) 查看某目录下有什么文件；3) 不确定文件路径时先探索。参数 path 可选，默认为项目根目录。示例：list_directory() 或 list_directory(path=\"src\")",
                parameters = listOf(
                    ToolParameter("path", "string", "目录路径。示例：src、tests。默认为 . 即项目根目录", required = false, default = "."),
                ),
                executor = { params ->
                    val path = params["path"] ?: "."
                    android.util.Log.e("NuclearBoy", "[DI] list_directory — path=$path")
                    when (val result = kotlinx.coroutines.runBlocking { fileOps.listDirectory(path) }) {
                        is AppResult.Success -> {
                            val listing = result.data.joinToString("\n") { f ->
                                val icon = if (f.isDirectory) "📁" else "📄"
                                "$icon ${f.name}  ${f.size.toFileSizeString()}"
                            }
                            android.util.Log.e("NuclearBoy", "[DI] list_directory SUCCESS — entries=${result.data.size}")
                            ToolResult(success = true, output = listing)
                        }
                        is AppResult.Failure -> {
                            android.util.Log.e("NuclearBoy", "[DI] list_directory FAILED — ${result.error.humanMessage}")
                            ToolResult(false, error = result.error.humanMessage)
                        }
                    }
                },
            ),
            ToolDefinition(
                name = "search_files",
                description = "按文件名搜索项目中的文件。使用场景：1) 找不到某个文件；2) 查找特定类型的文件；3) 搜索包含某关键词的文件名。query 是文件名的纯文本子串匹配（不是glob通配符，*不生效）。示例：search_files(query=\"README\") 可匹配 README.md",
                parameters = listOf(
                    ToolParameter("query", "string", "搜索关键词，纯文本子串匹配。示例：README、.py、test", required = true),
                ),
                executor = { params ->
                    val query = params["query"] ?: return@ToolDefinition ToolResult(false, error = "缺少 query 参数")
                    android.util.Log.e("NuclearBoy", "[DI] search_files — query=$query")
                    when (val result = kotlinx.coroutines.runBlocking { fileOps.searchFiles(query) }) {
                        is AppResult.Success -> {
                            val listing = result.data.joinToString("\n") { "📄 ${it.path}" }
                            android.util.Log.e("NuclearBoy", "[DI] search_files SUCCESS — found=${result.data.size}")
                            ToolResult(success = true, output = listing.ifEmpty { "未找到匹配的文件" })
                        }
                        is AppResult.Failure -> {
                            android.util.Log.e("NuclearBoy", "[DI] search_files FAILED — ${result.error.humanMessage}")
                            ToolResult(false, error = result.error.humanMessage)
                        }
                    }
                },
            ),
            ToolDefinition(
                name = "create_project",
                description = "创建新项目。参数 name 是项目名称。示例：create_project(name=\"my-project\")",
                parameters = listOf(
                    ToolParameter("name", "string", "项目名称。示例：my-project、家庭作业", required = true),
                    ToolParameter("tech_stack", "string", "技术栈（逗号分隔，如 python,fastapi）", required = false),
                ),
                executor = { params ->
                    val name = params["name"] ?: params["path"] ?: params["projectName"] ?: return@ToolDefinition ToolResult(false, error = "缺少 name 参数。请提供项目名称，如 name=\"my-project\"")
                    val techStack = params["tech_stack"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    android.util.Log.e("NuclearBoy", "[DI] create_project — name=$name, techStack=$techStack")
                    when (val result = kotlinx.coroutines.runBlocking { fileOps.createProject(name, techStack) }) {
                        is AppResult.Success -> {
                            android.util.Log.e("NuclearBoy", "[DI] create_project SUCCESS — id=${result.data.id}, name=${result.data.name}")
                            ToolResult(
                                success = true,
                                output = "项目 '${result.data.name}' 创建成功！路径: ${result.data.rootPath}",
                            )
                        }
                        is AppResult.Failure -> {
                            android.util.Log.e("NuclearBoy", "[DI] create_project FAILED — ${result.error.humanMessage}")
                            ToolResult(false, error = result.error.humanMessage)
                        }
                    }
                },
            ),
        )
    }

    private fun buildDocumentTools(docGen: DocumentGenerator): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "generate_docx",
                description = "生成Word文档。必填 path=文件名（如 report.docx）, title=标题, content=Markdown内容。使用场景：1) 用户让你生成文档；2) 需要导出报告；3) 创建Word文件。",
                parameters = listOf(
                    ToolParameter("path", "string", "输出文件路径。示例：report.docx、output/作业.docx", required = true),
                    ToolParameter("title", "string", "文档标题。示例：实验报告", required = true),
                    ToolParameter("content", "string", "文档内容（Markdown格式）。示例：# 标题\n正文内容", required = true),
                ),
                executor = { params ->
                    val path = params["path"] ?: params["output_path"] ?: return@ToolDefinition ToolResult(false, error = "缺少 path 参数")
                    val title = params["title"] ?: "未命名文档"
                    val content = params["content"] ?: ""
                    android.util.Log.e("NuclearBoy", "[DI] generate_docx — path=$path, title=${title.take(50)}, contentLen=${content.length}")
                    when (val result = kotlinx.coroutines.runBlocking { docGen.generateWordDocument(path, title, content) }) {
                        is AppResult.Success -> {
                            android.util.Log.e("NuclearBoy", "[DI] generate_docx SUCCESS — path=$path")
                            ToolResult(
                                success = true,
                                output = "Word 文档已生成: $path",
                                fileChanges = listOf(FileChange(path, ChangeType.CREATED, null)),
                            )
                        }
                        is AppResult.Failure -> {
                            android.util.Log.e("NuclearBoy", "[DI] generate_docx FAILED — ${result.error.humanMessage}")
                            ToolResult(false, error = result.error.humanMessage)
                        }
                    }
                },
            ),
            ToolDefinition(
                name = "generate_xlsx",
                description = "生成Excel电子表格。必填 path=文件名（如 data.xlsx）, sheet_data=JSON表格数据。使用场景：1) 用户让你生成表格；2) 需要导出数据；3) 创建Excel文件。",
                parameters = listOf(
                    ToolParameter("path", "string", "输出文件路径。示例：data.xlsx、报表/销售.xlsx", required = true),
                    ToolParameter("sheet_data", "string", "表格数据JSON。格式：{\"sheets\":[{\"name\":\"Sheet1\",\"headers\":[\"列1\"],\"rows\":[[\"值1\"]]}]}", required = true),
                ),
                executor = { params ->
                    val path = params["path"] ?: params["output_path"] ?: return@ToolDefinition ToolResult(false, error = "缺少 path 参数")
                    val sheetJson = params["sheet_data"] ?: return@ToolDefinition ToolResult(false, error = "缺少 sheet_data")
                    android.util.Log.e("NuclearBoy", "[DI] generate_xlsx — path=$path, sheetDataLen=${sheetJson.length}")
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                            .decodeFromString<kotlinx.serialization.json.JsonObject>(sheetJson)
                        val sheetsArray = parsed["sheets"]?.jsonArray ?: return@ToolDefinition ToolResult(false, error = "sheet_data 格式错误：需要 {\"sheets\": [...]}")
                        val sheets = sheetsArray.map { sheetEl ->
                            val s = sheetEl.jsonObject
                            com.nuclearboy.tools.docgen.SheetData(
                                name = s["name"]?.jsonPrimitive?.content ?: "Sheet1",
                                headers = s["headers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                                rows = s["rows"]?.jsonArray?.map { row ->
                                    row.jsonArray.map { it.jsonPrimitive.content }
                                } ?: emptyList(),
                            )
                        }
                        android.util.Log.e("NuclearBoy", "[DI] generate_xlsx — parsed ${sheets.size} sheets")
                        when (val result = kotlinx.coroutines.runBlocking { docGen.generateExcelSpreadsheet(path, sheets) }) {
                            is AppResult.Success -> {
                                android.util.Log.e("NuclearBoy", "[DI] generate_xlsx SUCCESS — path=$path")
                                ToolResult(
                                    success = true,
                                    output = "Excel 表格已生成: $path",
                                    fileChanges = listOf(FileChange(path, ChangeType.CREATED, null)),
                                )
                            }
                            is AppResult.Failure -> {
                                android.util.Log.e("NuclearBoy", "[DI] generate_xlsx FAILED — ${result.error.humanMessage}")
                                ToolResult(false, error = result.error.humanMessage)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NuclearBoy", "[DI] generate_xlsx EXCEPTION — ${e.message}")
                        ToolResult(false, error = "Excel 生成失败: ${e.message}")
                    }
                },
            ),
        )
    }

    private fun buildWebTools(sandbox: PythonSandbox) = listOf(
        ToolDefinition("web_search", "使用Bing搜索引擎搜索互联网。使用场景：1) 用户询问最新新闻或实时信息；2) 需要查找资料；3) 需要了解某个话题。参数 query 是搜索关键词。示例：web_search(query=\"今天科技新闻\")",
            listOf(ToolParameter("query", "string", "搜索关键词。示例：今天天气、Python教程、科技新闻", true),
                   ToolParameter("max_results", "integer", "返回结果数量，范围1-8。默认5", required = false, default = "5")),
            executor = { p ->
                val q = p["query"] ?: return@ToolDefinition ToolResult(false, error = "缺少 query")
                val n = (p["max_results"]?.toIntOrNull() ?: 5).coerceIn(0, 8)
                if (q.isBlank()) return@ToolDefinition ToolResult(false, error = "query 不能为空，请输入搜索关键词")
                if (n == 0) return@ToolDefinition ToolResult(true, "(无结果：max_results=0)")
                android.util.Log.e("NuclearBoy", "[DI] web_search — query=$q, max_results=$n")
                val script = """
import urllib.request, urllib.parse, re, http.cookiejar, time

def search(raw_query, n):
    start_ms = int(time.time() * 1000)
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    # Detect language: if >50% ASCII chars, use en-US market for better results
    ascii_cnt = sum(1 for c in raw_query if ord(c) < 128)
    mkt = 'en-US' if len(raw_query) > 0 and ascii_cnt / len(raw_query) > 0.5 else 'zh-CN'
    params = urllib.parse.urlencode({'q': raw_query, 'count': str(n), 'mkt': mkt, 'setlang': 'zh-cn'})
    url = "https://www.bing.com/search?" + params
    hdrs = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8"}
    req = urllib.request.Request(url, headers=hdrs)
    html = opener.open(req, timeout=12).read().decode('utf-8', errors='ignore')
    results = []
    # Primary: h2 > a pattern
    for m in re.finditer(r'<h2[^>]*><a[^>]*href="(https?://[^"]+)"[^>]*>(.*?)</a></h2>', html, re.I|re.S):
        link = m.group(1)
        title = re.sub(r'<[^>]+>', '', m.group(2)).strip()
        if title and len(title) > 4 and link.startswith("https://") and "bing.com" not in link and len(link) < 200:
            title = re.sub(r'^[a-zA-Z0-9.-]+\.[a-z]{2,}\s*[>]\s*', '', title)
            results.append("- [{0}]({1})".format(title, link))
        if len(results) >= n: break
    # Fallback: li.b_algo pattern
    if not results:
        for m in re.finditer(r'<li class="b_algo"[^>]*>(.*?)</li>', html, re.I|re.S):
            block = m.group(1)
            lm = re.search(r'<a[^>]*href="(https?://[^"]+)"[^>]*>(.*?)</a>', block, re.I|re.S)
            if lm and lm.group(1).startswith("https://") and "bing.com" not in lm.group(1):
                title = re.sub(r'<[^>]+>', '', lm.group(2)).strip()
                title = re.sub(r'^[a-zA-Z0-9.-]+\.[a-z]{2,}\s*[>]\s*', '', title)
                if title and len(title) > 4:
                    results.append("- [{0}]({1})".format(title, lm.group(1)))
            if len(results) >= n: break
    elapsed_ms = int(time.time() * 1000) - start_ms
    if results:
        for r in results: print(r)
        print(f"#meta: {len(results)} results in {elapsed_ms}ms, mkt={mkt}")
    else:
        print(f"#no_results: 未找到与'{raw_query[:50]}'相关的结果，请尝试其他关键词")

search(__Q__, __N__)
                """.trimIndent().replace("__Q__", "\"\"\"" + q.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"") + "\"\"\"").replace("__N__", n.toString())
                val r = runBlocking { sandbox.execute(script, ".") }
                val out = r.stdout.trim()
                val resultCount = out.lines().count { it.isNotBlank() }
                android.util.Log.e("NuclearBoy", "[DI] web_search result — resultCount=$resultCount, stderrLen=${r.stderr.length}")
                if (out.isNotBlank()) ToolResult(true, output = out.take(4000), error = r.stderr.ifBlank { null })
                else {
                    android.util.Log.e("NuclearBoy", "[DI] web_search NO RESULTS — stderr=${r.stderr.take(200)}")
                    ToolResult(false, error = "未找到结果: " + r.stderr.take(200))
                }
            }),
        ToolDefinition("web_fetch", "抓取网页的文本内容。使用场景：1) 需要阅读某个网页的详细内容；2) web_search 返回了链接需要深入了解；3) 用户提供了URL让你查看。参数 url 必须是完整的 https:// 链接。示例：web_fetch(url=\"https://example.com\")",
            listOf(ToolParameter("url", "string", "网页完整URL，必须以 https:// 开头。示例：https://example.com", true)),
            executor = { p ->
                val url = p["url"] ?: p["link"] ?: p["query"] ?: return@ToolDefinition ToolResult(false, error = "缺少 url 参数。请提供完整网址，如 url=\"https://example.com\"")
                android.util.Log.e("NuclearBoy", "[DI] web_fetch — url=$url")
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                    val req = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 NUCLEAR-BOY/1.0").build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val text = body.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(8000)
                    android.util.Log.e("NuclearBoy", "[DI] web_fetch SUCCESS — url=$url, bodyLen=${body.length}, textLen=${text.length}")
                    ToolResult(true, output = text)
                } catch (e: Exception) {
                    android.util.Log.e("NuclearBoy", "[DI] web_fetch FAILED — url=$url, error=${e.message}")
                    ToolResult(false, error = "抓取失败: " + e.message)
                }
            }),
    )

    private fun buildHardwareTools(sandbox: PythonSandbox) = listOf(
        ToolDefinition("device_vibrate", "控制手机振动。使用场景：1) 用户要求振动提醒；2) 完成任务后给触感反馈；3) 演奏振动旋律。参数 pattern=振动模式或时长ms。",
            listOf(ToolParameter("pattern", "string", "振动模式：数字=毫秒数(如200)，或逗号分隔的波形(如0,100,200,100)", true)),
            executor = { p ->
                val pattern = p["pattern"] ?: return@ToolDefinition ToolResult(false, error = "缺少 pattern")
                val script = """
from skills.android_bridge import AndroidBridge
ab = AndroidBridge()
p = "__P__"
if "," in p:
    nums = [int(x.strip()) for x in p.split(",") if x.strip().isdigit()]
    ab.vibrate_pattern(nums)
    print("VIBRATE_OK:pattern")
else:
    ms = int(p) if p.isdigit() else 200
    ab.vibrate(ms)
    print("VIBRATE_OK:" + str(ms) + "ms")
""".replace("__P__", pattern.replace("\"", "\\\""))
                val r = runBlocking { sandbox.execute(script, ".") }
                if (r.exitCode == 0) ToolResult(true, r.stdout.trim())
                else ToolResult(false, error = r.stderr.ifBlank { "振动失败" })
            }),
        ToolDefinition("device_clipboard", "读取或写入手机剪贴板。使用场景：1) 用户让你复制内容；2) 需要粘贴剪贴板内容；3) 读写文本数据。参数 action=read/write，若write需text参数。",
            listOf(ToolParameter("action", "string", "read 或 write", true),
                   ToolParameter("text", "string", "要写入的内容（仅action=write时需要）", required = false)),
            executor = { p ->
                val action = p["action"] ?: "read"
                when (action) {
                    "read" -> {
                        val r = runBlocking { sandbox.execute("from skills.android_bridge import AndroidBridge; print(AndroidBridge().clipboard_paste() or '')", ".") }
                        ToolResult(true, r.stdout.trim().ifEmpty { "(剪贴板为空)" })
                    }
                    "write" -> {
                        val text = p["text"] ?: return@ToolDefinition ToolResult(false, error = "写入需要 text 参数")
                        val r = runBlocking { sandbox.execute("from skills.android_bridge import AndroidBridge; AndroidBridge().clipboard_copy('''${text.replace("'", "\\'")}'''); print('CLIPBOARD_OK')", ".") }
                        if (r.exitCode == 0) ToolResult(true, "已复制到剪贴板")
                        else ToolResult(false, error = r.stderr)
                    }
                    else -> ToolResult(false, error = "action 必须是 read 或 write")
                }
            }),
        ToolDefinition("device_battery", "获取手机电池信息（电量百分比、温度、充电状态等）。使用场景：1) 用户询问电量；2) 需要检查充电状态。无参数。",
            listOf(),
            executor = {
                val r = runBlocking { sandbox.execute("from skills.android_bridge import AndroidBridge; ab=AndroidBridge(); print(ab.print_battery())", ".") }
                ToolResult(r.exitCode == 0, r.stdout.ifBlank { r.stderr }.trim())
            }),
        ToolDefinition("device_info", "获取手机系统信息（品牌、型号、Android版本等）。使用场景：1) 用户询问设备信息；2) 调试时了解运行环境。无参数。",
            listOf(),
            executor = {
                val r = runBlocking { sandbox.execute("from skills.android_bridge import AndroidBridge; print(AndroidBridge().print_system_info())", ".") }
                ToolResult(r.exitCode == 0, r.stdout.ifBlank { r.stderr }.trim())
            }),
        ToolDefinition("device_flashlight", "控制手机闪光灯。使用场景：1) 用户需要手电筒；2) 发送光信号；3) 闪烁提醒。参数 action=on/off/blink。⚠️ 需要CAMERA权限。",
            listOf(ToolParameter("action", "string", "on=打开, off=关闭, blink=闪烁", true)),
            requiresConfirmation = true,
            executor = { p ->
                val action = p["action"] ?: "on"
                val script = when (action) {
                    "on" -> "from skills.android_bridge import AndroidBridge; ok=AndroidBridge().flashlight_on(); print('FLASH_ON' if ok else 'FAIL:无权限或无效')"
                    "off" -> "from skills.android_bridge import AndroidBridge; AndroidBridge().flashlight_off(); print('FLASH_OFF')"
                    "blink" -> "from skills.android_bridge import AndroidBridge; AndroidBridge().flashlight_blink(3); print('FLASH_BLINK')"
                    else -> return@ToolDefinition ToolResult(false, error = "action 必须是 on/off/blink")
                }
                val r = runBlocking { sandbox.execute(script, ".") }
                ToolResult(r.exitCode == 0, r.stdout.trim(), error = r.stderr.ifBlank { null })
            }),
    )
}
