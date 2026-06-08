package com.nuclearboy.agent

import com.nuclearboy.api.deepseek.FunctionDefinitionDto
import com.nuclearboy.api.deepseek.ToolDefinitionDto
import com.nuclearboy.common.AppError
import com.nuclearboy.common.AppResult
import com.nuclearboy.common.ChangeType
import com.nuclearboy.common.FileChange
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/**
 * A tool that the agent can invoke.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val executor: suspend (Map<String, String>) -> ToolResult,
    val requiresConfirmation: Boolean = false,
)

/**
 * A single parameter for a tool.
 */
data class ToolParameter(
    val name: String,
    val type: String, // "string", "integer", "boolean"
    val description: String,
    val required: Boolean = true,
    val default: String? = null,
    val enum: List<String>? = null,
)

/**
 * Result of executing a tool.
 */
data class ToolResult(
    val success: Boolean,
    val output: String = "",
    val fileChanges: List<FileChange> = emptyList(),
    val error: String? = null,
) {
    companion object {
        fun success(output: String, fileChanges: List<FileChange> = emptyList()): ToolResult =
            ToolResult(success = true, output = output, fileChanges = fileChanges)

        fun failure(error: String): ToolResult =
            ToolResult(success = false, output = "", error = error)
    }
}

/**
 * Registry that manages all available tools for the agent.
 *
 * Thread-safe via [Mutex]. Converts registered tools into DeepSeek-compatible
 * [ToolDefinitionDto] lists and dispatches tool calls to the correct executor.
 *
 * Integration points:
 * - Skills module: skills can register their own tools
 * - PythonBridge module: document generation and script execution tools
 * - Tools-docgen module: Word/Excel file manipulation tools
 */
class ToolRegistry {

    private val mutex = Mutex()
    private val tools = mutableMapOf<String, ToolDefinition>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // ── External module references (set after construction) ──

    /** Optional reference to the Skills module for skill-based tool execution. */
    var skillsExecutor: (suspend (String, Map<String, String>) -> ToolResult)? = null

    /** Optional reference to PythonBridge for sandbox execution. */
    var pythonExecutor: (suspend (String, Map<String, String>) -> ToolResult)? = null

    // ── Registration ─────────────────────────────────────

    /**
     * Register a single tool. Overwrites if a tool with the same name already exists.
     */
    suspend fun register(tool: ToolDefinition): AppResult<Boolean> {
        android.util.Log.e("NuclearBoy", "[ToolReg] register() toolName=${tool.name}")
        mutex.withLock {
            tools[tool.name] = tool
        }
        return AppResult.success(true)
    }

    /**
     * Register multiple tools at once.
     */
    suspend fun registerAll(newTools: List<ToolDefinition>): AppResult<Boolean> {
        android.util.Log.e("NuclearBoy", "[ToolReg] registerAll() toolCount=${newTools.size} names=${newTools.joinToString { it.name }}")
        mutex.withLock {
            newTools.forEach { tool ->
                tools[tool.name] = tool
            }
        }
        return AppResult.success(true)
    }

    /**
     * Unregister a tool by name.
     */
    suspend fun unregister(name: String): AppResult<Boolean> {
        android.util.Log.e("NuclearBoy", "[ToolReg] unregister() toolName=$name")
        return mutex.withLock {
            if (tools.remove(name) != null) {
                AppResult.success(true)
            } else {
                AppResult.failure(
                    AppError.SkillNotFound,
                    "工具 \"$name\" 未注册，无法移除"
                )
            }
        }
    }

    /**
     * Check if a tool is registered.
     */
    suspend fun hasTool(name: String): Boolean {
        mutex.withLock {
            return tools.containsKey(name)
        }
    }

    /**
     * Get a single tool definition by name.
     */
    suspend fun getTool(name: String): ToolDefinition? {
        mutex.withLock {
            val tool = tools[name]
            android.util.Log.e("NuclearBoy", "[ToolReg] getTool() toolName=$name found=${tool != null}")
            return tool
        }
    }

    /**
     * Get the count of registered tools.
     */
    suspend fun toolCount(): Int {
        mutex.withLock {
            return tools.size
        }
    }

    // ── DeepSeek API Conversion ──────────────────────────

    /**
     * Build a list of [ToolDefinitionDto] suitable for sending to the DeepSeek API.
     * Only returns tools that fit within the budget (AppConstants.BUDGET_TOOL_DEFINITIONS).
     * If the full list is too large, returns the most commonly used subset first.
     */
    suspend fun toDeepSeekToolDefinitions(maxTokens: Long = 5000): List<ToolDefinitionDto> {
        mutex.withLock {
            val totalTools = tools.size
            val result = mutableListOf<ToolDefinitionDto>()
            var estimatedTokens = 0L

            // Sort: prioritize Python/file tools first so they never get truncated
            val priorityTools = setOf("run_python", "read_file", "write_file", "list_directory")
            val sorted = tools.values.sortedBy { tool ->
                when {
                    tool.name in priorityTools -> 0  // Highest priority
                    tool.requiresConfirmation -> 2   // Confirmation-required tools last
                    else -> 1                         // Normal tools in middle
                }
            }

            for (tool in sorted) {
                val def = convertToDto(tool)
                val tokenEstimate = estimateToolDefTokens(def)
                if (estimatedTokens + tokenEstimate > maxTokens) break
                result.add(def)
                estimatedTokens += tokenEstimate
            }

            val excluded = totalTools - result.size
            android.util.Log.e("NuclearBoy", "[ToolReg] toDeepSeekToolDefinitions() total=$totalTools included=${result.size} excluded=$excluded budget=$maxTokens tokensUsed=$estimatedTokens")
            return result
        }
    }

    /**
     * Get tool definitions for specific tool names.
     */
    suspend fun toDeepSeekToolDefinitionsFor(vararg names: String): List<ToolDefinitionDto> {
        mutex.withLock {
            return names.mapNotNull { name ->
                tools[name]?.let { convertToDto(it) }
            }
        }
    }

    // ── Execution ────────────────────────────────────────

    /**
     * Execute a tool by name with the given parameters.
     *
     * Returns [AppResult.Failure] if the tool is not found.
     * Execution errors are captured in [ToolResult.error], not in the AppResult wrapper,
     * so the agent loop can feed the error back to the model as a tool response.
     */
    suspend fun execute(name: String, parameters: Map<String, String>): AppResult<ToolResult> {
        val tool = mutex.withLock { tools[name] }

        if (tool == null) {
            // Try external modules
            return executeViaExternalModule(name, parameters)
        }

        return AppResult.runCatching {
            tool.executor(parameters)
        }
    }

    /**
     * Execute a tool and return its result, feeding errors into the output
     * so the model can self-correct. Never returns a failure AppResult for
     * tool execution — failures are encoded as ToolResult(success=false).
     */
    suspend fun executeSafe(name: String, parameters: Map<String, String>): ToolResult {
        android.util.Log.e("NuclearBoy", "[ToolReg] executeSafe() toolName=$name paramsKeys=${parameters.keys}")
        val startTime = System.currentTimeMillis()
        val toolDef = mutex.withLock { tools[name] }
        android.util.Log.e("NuclearBoy", "[ToolReg] executeSafe() toolFound=${toolDef != null}")
        val result = when (val execResult = execute(name, parameters)) {
            is AppResult.Success -> execResult.data
            is AppResult.Failure -> {
                // Append parameter hints so the model can correct itself
                val paramHint = toolDef?.parameters?.filter { it.required }
                    ?.joinToString(", ") { "${it.name} (${it.type})" }
                val errorMsg = if (paramHint != null) {
                    "${execResult.error.humanMessage}。需要的参数: $paramHint"
                } else {
                    execResult.error.humanMessage
                }
                ToolResult.failure(errorMsg)
            }
        }
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.e("NuclearBoy", "[ToolReg] executeSafe() result: success=${result.success} outputLen=${result.output.length} duration=${duration}ms error=${result.error}")
        return result
    }

    // ── Default Tools ────────────────────────────────────

    /**
     * Register the standard set of built-in tools (file operations, etc.).
     * Call this once during AgentEngine initialization.
     */
    suspend fun registerDefaultTools() {
        android.util.Log.e("NuclearBoy", "[ToolReg] registerDefaultTools() entry")
        registerAll(
            listOf(
                // --- File Read ---
                ToolDefinition(
                    name = "read_file",
                    description = "读取指定文件的内容。支持文本文件和代码文件，自动检测编码。",
                    parameters = listOf(
                        ToolParameter("filePath", "string", "文件的完整路径", required = true),
                        ToolParameter("offset", "integer", "从第几行开始读取（0-based）", required = false, default = "0"),
                        ToolParameter("limit", "integer", "最多读取多少行", required = false, default = "500"),
                    ),
                    executor = { params ->
                        val path = params["filePath"] ?: ""
                        ToolResult.failure("read_file 需要外部文件系统实现 — 路径: $path")
                    },
                ),

                // --- File Write ---
                ToolDefinition(
                    name = "write_file",
                    description = "创建或覆盖一个文件。会自动创建父目录。",
                    parameters = listOf(
                        ToolParameter("filePath", "string", "文件的完整路径", required = true),
                        ToolParameter("content", "string", "要写入的内容", required = true),
                    ),
                    requiresConfirmation = true,
                    executor = { params ->
                        val path = params["filePath"] ?: ""
                        ToolResult.failure("write_file 需要外部文件系统实现 — 路径: $path")
                    },
                ),

                // --- File Search ---
                ToolDefinition(
                    name = "search_files",
                    description = "在项目中搜索文件。支持 glob 模式匹配。",
                    parameters = listOf(
                        ToolParameter("pattern", "string", "搜索模式，如 **/*.kt", required = true),
                        ToolParameter("directory", "string", "搜索的根目录", required = false, default = "."),
                    ),
                    executor = { params ->
                        val pattern = params["pattern"] ?: "*"
                        ToolResult.success("搜索完成: 模式 \"$pattern\"（需要外部文件系统实现）")
                    },
                ),

                // --- List Directory ---
                ToolDefinition(
                    name = "list_directory",
                    description = "列出目录中的文件和子目录。",
                    parameters = listOf(
                        ToolParameter("path", "string", "目录路径", required = true),
                    ),
                    executor = { params ->
                        val path = params["path"] ?: "."
                        ToolResult.success("目录列表: $path（需要外部文件系统实现）")
                    },
                ),

                // --- Run Python Script ---
                ToolDefinition(
                    name = "run_python",
                    description = "在 Python 3.11 沙箱中直接执行 Python 代码并返回执行结果。你可以用它来运行脚本、测试代码、处理数据、生成文档等。沙箱已就绪，随时可用。",
                    parameters = listOf(
                        ToolParameter("path", "string", "要执行的 Python 代码（完整脚本）", required = true),
                        ToolParameter("workingDir", "string", "工作目录", required = false, default = "."),
                        ToolParameter("timeout", "integer", "超时秒数（默认 120）", required = false, default = "120"),
                    ),
                    requiresConfirmation = false,
                    executor = { params ->
                        // Attempt Python executor if available
                        pythonExecutor?.let { exec ->
                            exec("run_python", params)
                        } ?: ToolResult.failure("Python 运行时未初始化")
                    },
                ),

                // --- Web Search ---
                ToolDefinition(
                    name = "web_search",
                    description = "搜索网络获取最新信息。",
                    parameters = listOf(
                        ToolParameter("query", "string", "搜索查询", required = true),
                    ),
                    executor = { params ->
                        val query = params["query"] ?: ""
                        ToolResult.failure("web_search 需要网络搜索实现 — 查询: $query")
                    },
                ),

                // --- Web Fetch ---
                ToolDefinition(
                    name = "web_fetch",
                    description = "获取指定 URL 的内容。",
                    parameters = listOf(
                        ToolParameter("url", "string", "要获取的 URL", required = true),
                    ),
                    executor = { params ->
                        val url = params["url"] ?: ""
                        ToolResult.failure("web_fetch 需要网络实现 — URL: $url")
                    },
                ),
            )
        )
        android.util.Log.e("NuclearBoy", "[ToolReg] registerDefaultTools() registered read_file, write_file, search_files, list_directory, run_python, web_search, web_fetch")
    }

    // ── Private ──────────────────────────────────────────

    private fun convertToDto(tool: ToolDefinition): ToolDefinitionDto {
        // Build the JSON Schema parameters object
        val propertiesJson = JsonObject(
            tool.parameters.associate { param ->
                param.name to buildParamSchemaJson(param)
            }
        )

        val requiredList = tool.parameters
            .filter { it.required }
            .map { it.name }

        val paramsMap = mutableMapOf<String, JsonElement?>()
        paramsMap["type"] = JsonPrimitive("object")
        paramsMap["properties"] = propertiesJson
        if (requiredList.isNotEmpty()) {
            paramsMap["required"] = JsonArray(requiredList.map { JsonPrimitive(it) })
        }
        paramsMap["additionalProperties"] = JsonPrimitive(false)

        @Suppress("UNCHECKED_CAST")
        val paramsObj = JsonObject(paramsMap.filterValues { it != null } as Map<String, JsonElement>)

        return ToolDefinitionDto(
            function = FunctionDefinitionDto(
                name = tool.name,
                description = if (tool.requiresConfirmation) {
                    "${tool.description}\n[⚠️ 此操作需要用户确认]"
                } else {
                    tool.description
                },
                parameters = paramsObj,
            )
        )
    }

    private fun buildParamSchemaJson(param: ToolParameter): JsonObject {
        val fields = mutableMapOf<String, JsonElement?>()
        fields["type"] = JsonPrimitive(param.type)
        fields["description"] = JsonPrimitive(param.description)

        if (param.default != null) {
            fields["default"] = when (param.type) {
                "integer" -> {
                    val num = param.default.toLongOrNull()
                    if (num != null) JsonPrimitive(num) else JsonPrimitive(param.default)
                }
                "boolean" -> {
                    val bool = param.default.toBooleanStrictOrNull()
                    if (bool != null) JsonPrimitive(bool) else JsonPrimitive(param.default)
                }
                else -> JsonPrimitive(param.default)
            }
        }

        if (param.enum != null) {
            fields["enum"] = JsonArray(param.enum.map { JsonPrimitive(it) })
        }

        @Suppress("UNCHECKED_CAST")
        val safeFields = fields.filterValues { it != null } as Map<String, JsonElement>
        return JsonObject(safeFields)
    }

    private fun estimateToolDefTokens(def: ToolDefinitionDto): Long {
        val text = def.function.name + def.function.description +
                (def.function.parameters?.toString() ?: "")
        return (text.length / 3.5).toLong().coerceAtLeast(20)
    }

    private suspend fun executeViaExternalModule(
        name: String,
        params: Map<String, String>,
    ): AppResult<ToolResult> {
        // Try Python executor for tool names that look like Python tools
        if (name in listOf("run_python", "execute_shell")) {
            pythonExecutor?.let { exec ->
                return AppResult.runCatching { exec(name, params) }
            }
        }

        // Try skills executor as fallback
        skillsExecutor?.let { exec ->
            return AppResult.runCatching { exec(name, params) }
        }

        return AppResult.failure(
            AppError.SkillNotFound,
            "找不到工具 \"$name\"。检查一下工具名是否拼写正确？"
        )
    }

    /**
     * Serialize parameters map into a JSON string for tool call DTOs.
     */
    fun paramsToJson(params: Map<String, String>): String {
        return json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(),
            params
        )
    }
}
