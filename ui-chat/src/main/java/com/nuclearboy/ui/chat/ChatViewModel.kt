package com.nuclearboy.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuclearboy.agent.AgentEngine
import com.nuclearboy.agent.AgentEvent
import com.nuclearboy.agent.ProjectContext
import com.nuclearboy.api.deepseek.ContextBudget
import com.nuclearboy.api.deepseek.ContextWindowManager
import com.nuclearboy.api.deepseek.TokenSnapshot
import com.nuclearboy.api.deepseek.TokenTracker
import com.nuclearboy.common.*
import com.nuclearboy.common.AppConstants
import com.nuclearboy.common.AppResult
import com.nuclearboy.memory.MemoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID
import javax.inject.Inject

// ── UI State ────────────────────────────────────────────────────────────────

data class StreamingState(
    val messageId: String,
    val thinkingText: StringBuilder = StringBuilder(),
    val responseText: StringBuilder = StringBuilder(),
    val activeToolCalls: List<ToolCallRecord> = emptyList(),
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val tokenSnapshot: TokenSnapshot = TokenSnapshot(),
    val contextBudget: ContextBudget = ContextBudget(),
    val streamingState: StreamingState? = null,
    val scrollToBottom: Long = 0L, // Incremented to trigger scroll
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val tokenTracker: TokenTracker,
    private val contextManager: ContextWindowManager,
    private val apiKeyManager: com.nuclearboy.api.deepseek.ApiKeyManager,
    private val fileOperations: com.nuclearboy.tools.docgen.FileOperations,
    private val skillManager: com.nuclearboy.skills.SkillManager,
    private val memoryStore: MemoryStore,
) : ViewModel() {

    // 从记忆加载用户画像
    private suspend fun loadUserProfile(): UserProfile {
        val result = memoryStore.exportUserProfile()
        return when (result) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> UserProfile()
        }
    }

    /** Set by NavHost to enable background notifications */
    var notificationCallback: ((String, String?) -> Unit)? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _isProcessing = MutableStateFlow(false)
    private val _streamingState = MutableStateFlow<StreamingState?>(null)
    private val _scrollToBottom = MutableStateFlow(0L)

    private var agentJob: Job? = null
    private var lastUserMessage: ChatMessage? = null
    private var currentProjectId: String? = null
    private var currentThinkingId: String? = null
    private var currentAssistantMsgId: String? = null
    private var selectedMode: Int = 0
    private val _projectName = MutableStateFlow("")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    fun setMode(mode: Int) { selectedMode = mode.coerceIn(0, 2) }

    private val _projectFiles = MutableStateFlow<List<FileInfo>>(emptyList())
    val projectFiles: StateFlow<List<FileInfo>> = _projectFiles.asStateFlow()

    fun setProject(projectId: String) {
        android.util.Log.e("NuclearBoy", "[ChatVM] setProject() projectId=$projectId previousId=$currentProjectId currentDir=${fileOperations.currentProjectDir}")
        // currentProjectDir 由外部 selectProject() 设置（UUID → 目录名的转换）
        // 此处不覆盖，信任外部已设置正确
        currentProjectId = projectId
        val root = fileOperations.projectRoot()
        _projectName.value = if (projectId == "__general__") "核弹男孩" else root.name
        // 每次切换都重新加载消息
        val loaded = try { loadPersistedMessages(projectId) }
            catch (e: Exception) { android.util.Log.e("NuclearBoy", "加载历史失败: ${e.message}"); emptyList() }
        android.util.Log.e("NuclearBoy", "[ChatVM] setProject() messagesLoaded=${loaded.size} root=${root.absolutePath}")
        _messages.value = loaded
        refreshProjectFiles()
        if (loaded.isNotEmpty()) _scrollToBottom.value++
        if (projectId != "__general__") {
            try {
                val skillsDir = java.io.File(fileOperations.projectRoot(), AppConstants.PROJECT_SKILLS_DIR)
                skillManager.loadProjectSkills(skillsDir)
            } catch (e: Exception) { android.util.Log.e("NuclearBoy", "[ChatVM] setProject() skills load failed: ${e.message}") }
        }
    }

    override fun onCleared() {
        android.util.Log.e("NuclearBoy", "[ChatVM] onCleared() ViewModel cleanup")
        super.onCleared()
        skillManager.unloadProjectSkills()
    }

    private val _browseDir = MutableStateFlow(".")
    val browseDir: StateFlow<String> = _browseDir.asStateFlow()

    fun refreshProjectFiles(path: String = ".") {
        android.util.Log.e("NuclearBoy", "[ChatVM] refreshProjectFiles() path=$path")
        viewModelScope.launch {
            _browseDir.value = path
            val result = fileOperations.listDirectory(path)
            if (result is AppResult.Success) {
                _projectFiles.value = result.data
                android.util.Log.e("NuclearBoy", "[ChatVM] refreshProjectFiles() filesFound=${result.data.size} path=$path")
            }
        }
    }

    fun navigateToDir(dirName: String) {
        val current = _browseDir.value
        val newPath = if (current == ".") dirName else "$current/$dirName"
        android.util.Log.e("NuclearBoy", "[ChatVM] navigateToDir() '$current' -> '$newPath'")
        refreshProjectFiles(newPath)
    }

    fun navigateUp() {
        val current = _browseDir.value
        if (current == ".") return
        val parent = current.substringBeforeLast("/", ".")
        val newPath = if (parent.isEmpty()) "." else parent
        android.util.Log.e("NuclearBoy", "[ChatVM] navigateUp() '$current' -> '$newPath'")
        refreshProjectFiles(newPath)
    }

    private fun saveMessages() {
        val pid = currentProjectId ?: return
        android.util.Log.e("NuclearBoy", "[ChatVM] saveMessages() pid=$pid messagesCount=${_messages.value.size}")
        try {
            // 直接用 workspaceRoot + projectId 构建路径，不依赖 currentProjectDir
            val dir = java.io.File(fileOperations.getWorkspaceRoot(), "$pid/.agent")
            dir.mkdirs()
            val data = Json.encodeToString(serializer(), _messages.value.takeLast(50))
            val file = java.io.File(dir, "conversation.json")
            file.writeText(data)
            android.util.Log.e("NuclearBoy", "[ChatVM] saveMessages() saved to ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("NuclearBoy", "[ChatVM] saveMessages() error: ${e.message}", e)
        }
    }

    private fun loadPersistedMessages(projectId: String): List<ChatMessage> {
        android.util.Log.e("NuclearBoy", "[ChatVM] loadPersistedMessages() projectId=$projectId")
        return try {
            val file = java.io.File(fileOperations.getWorkspaceRoot(), "$projectId/.agent/conversation.json")
            android.util.Log.e("NuclearBoy", "[ChatVM] loadPersistedMessages() path=${file.absolutePath} exists=${file.exists()}")
            if (file.exists()) {
                val loaded = Json.decodeFromString(serializer<List<ChatMessage>>(), file.readText())
                android.util.Log.e("NuclearBoy", "[ChatVM] loadPersistedMessages() loaded=${loaded.size}")
                loaded
            } else emptyList()
        } catch (e: Exception) {
            android.util.Log.e("NuclearBoy", "[ChatVM] loadPersistedMessages() error: ${e.message}", e)
            android.util.Log.e("NuclearBoy", "加载消息失败: ${e.message}", e)
            emptyList()
        }
    }

    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    val streamingState: StateFlow<StreamingState?> = _streamingState.asStateFlow()
    val scrollToBottom: StateFlow<Long> = _scrollToBottom.asStateFlow()

    /** Expose project root path for file browser integration. */
    fun getProjectRoot(): String = fileOperations.projectRoot().absolutePath

    fun getActiveSkillCount(): Int = skillManager.activeSkills.value.size

    val uiState: StateFlow<ChatUiState> = combine(
        combine(_messages, _isProcessing, tokenTracker.snapshot) { msgs, processing, tokens ->
            Triple(msgs, processing, tokens)
        },
        combine(contextManager.budget, _streamingState, _scrollToBottom) { budget, streaming, scroll ->
            Triple(budget, streaming, scroll)
        },
    ) { (msgs, processing, tokens), (budget, streaming, scroll) ->
        ChatUiState(
            messages = msgs,
            isProcessing = processing,
            tokenSnapshot = tokens,
            contextBudget = budget,
            streamingState = streaming,
            scrollToBottom = scroll,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    // ── Public actions ──────────────────────────────────────────────────

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() entry textLen=${trimmed.length} isProcessing=${_isProcessing.value}")
        android.util.Log.e("NuclearBoy", "SEND: '$trimmed' isProcessing=${_isProcessing.value}")
        if (trimmed.isEmpty() || _isProcessing.value) return

        // Check API key
        val key = apiKeyManager.getActiveKey()
        android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() apiKey status: hasKey=${key != null} keyPreview=${key?.take(10)}")
        android.util.Log.e("NuclearBoy", "SEND: key=${key?.take(10)}... isNull=${key==null}")
        if (key == null) {
            android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() no API key, showing tip")
            val userMessage = ChatMessage(role = MessageRole.USER, content = trimmed, status = MessageStatus.COMPLETE)
            _messages.update { it + userMessage }
            val tipMsg = ChatMessage(
                role = MessageRole.SYSTEM,
                content = "需要配置 DeepSeek API Key 才能开始\n\n请到右上角「设置」输入你的 Key（sk-v4- 开头），保存后即可使用",
                status = MessageStatus.COMPLETE,
            )
            _messages.update { it + tipMsg }
            _scrollToBottom.value++
            return
        }

        // Cancel any existing processing cleanly
        cancelCurrentOperation()

        val userMessage = ChatMessage(
            role = MessageRole.USER, content = trimmed, status = MessageStatus.COMPLETE,
        )
        _messages.update { it + userMessage }
        android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() userMessage created id=${userMessage.id}")
        lastUserMessage = userMessage
        saveMessages()
        _scrollToBottom.value++
        _isProcessing.value = true

        // Create single ASSISTANT placeholder — all updates stream into this message
        val assistantId = UUID.randomUUID().toString()
        currentThinkingId = assistantId
        currentAssistantMsgId = assistantId
        val placeholder = ChatMessage(
            id = assistantId, role = MessageRole.ASSISTANT,
            content = "", status = MessageStatus.THINKING,
        )
        _messages.update { it + placeholder }
        android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() assistant placeholder created id=$assistantId")
        _streamingState.value = StreamingState(messageId = assistantId, isThinking = true)
        _scrollToBottom.value++

        // 读记忆文件
        val memFile = java.io.File(fileOperations.getWorkspaceRoot(), "__general__/.agent/memory.json")
        val memoryCtx = if (memFile.exists()) {
            try {
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val memories = json.decodeFromString<List<Map<String, String>>>(memFile.readText())
                memories.takeLast(10).joinToString("\n") { "- ${it["value"]} [${it["category"]}]" }
            } catch (_: Exception) { "" }
        } else ""

        // Build project context with memory
        val projectContext = ProjectContext(
            project = currentProjectId?.let { id ->
                Project(id = id, name = id, rootPath = fileOperations.projectRoot().absolutePath)
            },
            currentFiles = _projectFiles.value,
            userProfile = UserProfile(),
            activeSkills = skillManager.activeSkills.value,
            memoryContext = memoryCtx,
        )
        android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() projectContext built: project=${projectContext.project?.name} files=${projectContext.currentFiles.size} skills=${projectContext.activeSkills.size}")

        notificationCallback?.invoke("thinking", currentProjectId)
        android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() notificationCallback invoked with 'thinking'")
        android.util.Log.e("NuclearBoy", "SEND: launching coroutine...")

        agentJob = viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() coroutine started on IO dispatcher")
            android.util.Log.e("NuclearBoy", "SEND: coroutine started!")
            // 从记忆加载用户偏好
            val loadedProfile = loadUserProfile()
            val enrichedContext = projectContext.copy(userProfile = loadedProfile)
            try {
                agentEngine.processMessage(
                    userMessage = trimmed,
                    projectContext = enrichedContext,
                    conversationHistory = _messages.value.filter { it.role != MessageRole.SYSTEM },
                    userMode = selectedMode,
                ).collect { event ->
                    handleAgentEvent(event, assistantId)
                }
                android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() coroutine flow completed normally")
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() coroutine cancelled")
                updateAssistantMessage(assistantId) { msg ->
                    if (msg.content.isEmpty()) msg.copy(content = "", status = MessageStatus.CANCELLED)
                    else msg.copy(status = MessageStatus.COMPLETE)
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() coroutine error: ${e.message}", e)
                handleAgentError(e, assistantId)
            } finally {
                android.util.Log.e("NuclearBoy", "[ChatVM] sendMessage() coroutine entering finally block")
                finalizeProcessing(assistantId)
            }
        }
    }

    fun retryLastMessage() {
        android.util.Log.e("NuclearBoy", "[ChatVM] retryLastMessage() entry isProcessing=${_isProcessing.value}")
        if (_isProcessing.value) return
        val lastUser = lastUserMessage ?: return
        val currentMessages = _messages.value.toMutableList()
        val lastUserIndex = currentMessages.indexOfLast { it.id == lastUser.id }
        if (lastUserIndex >= 0 && lastUserIndex < currentMessages.lastIndex) {
            currentMessages.subList(lastUserIndex + 1, currentMessages.size).clear()
            _messages.value = currentMessages
        }
        sendMessage(lastUser.content)
    }

    fun cancelCurrentOperation() {
        val job = agentJob
        val wasActive = job != null && job.isActive
        android.util.Log.e("NuclearBoy", "[ChatVM] cancelCurrentOperation() jobActive=$wasActive")
        if (job != null && job.isActive) {
            job.cancel()
            agentEngine.cancel()
        }
        agentJob = null
        _isProcessing.value = false
        _streamingState.value = null
        currentThinkingId = null
        currentAssistantMsgId = null
    }

    fun clearConversation() {
        android.util.Log.e("NuclearBoy", "[ChatVM] clearConversation() entry messagesCount=${_messages.value.size}")
        cancelCurrentOperation()
        _messages.value = emptyList()
        lastUserMessage = null
        contextManager.reset()
        currentThinkingId = null
        currentAssistantMsgId = null
    }

    // ── Private: event handling ─────────────────────────────────────────

    private suspend fun handleAgentEvent(event: AgentEvent, thinkingId: String) {
        when (event) {
            is AgentEvent.Thinking -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() Thinking msgLen=${event.message.length}")
                _streamingState.update { current ->
                    (current ?: StreamingState(thinkingId)).copy(
                        isThinking = true,
                        thinkingText = (current?.thinkingText ?: StringBuilder()).append(event.message),
                    )
                }
                updateAssistantMessage(thinkingId) { msg ->
                    msg.copy(
                        status = MessageStatus.THINKING,
                        reasoningContent = _streamingState.value?.thinkingText?.toString(),
                    )
                }
            }

            is AgentEvent.StreamContent -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() StreamContent textLen=${event.text.length}")
                _streamingState.update { current ->
                    (current ?: StreamingState(thinkingId)).copy(
                        isThinking = false,
                        isStreaming = true,
                        responseText = (current?.responseText ?: StringBuilder()).append(event.text),
                    )
                }
                val streamedContent = _streamingState.value?.responseText?.toString() ?: event.text
                updateAssistantMessage(thinkingId) { msg ->
                    msg.copy(
                        content = streamedContent,
                        status = MessageStatus.STREAMING,
                    )
                }
            }

            is AgentEvent.Response -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() Response contentLen=${event.message.content.length} toolCalls=${event.message.toolCalls.size}")
                updateAssistantMessage(thinkingId) { msg ->
                    val finalContent = if (event.message.content.isNotBlank())
                        event.message.content else msg.content
                    msg.copy(
                        content = finalContent,
                        reasoningContent = event.message.reasoningContent,
                        toolCalls = event.message.toolCalls.ifEmpty { msg.toolCalls },
                        tokenUsage = event.message.tokenUsage,
                        status = MessageStatus.COMPLETE,
                    )
                }
            }

            is AgentEvent.ToolExecution -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() ToolExecution toolName=${event.toolName} status=${event.status} toolCallId=${event.toolCallId}")
                val record = ToolCallRecord(
                    toolName = event.toolName,
                    input = "",
                    status = event.status,
                    toolCallId = event.toolCallId,
                )
                _streamingState.update { current ->
                    (current ?: StreamingState(thinkingId)).copy(
                        activeToolCalls = (current?.activeToolCalls ?: emptyList()) + record,
                    )
                }
                updateAssistantMessage(thinkingId) { msg ->
                    val existingIdx = msg.toolCalls.indexOfFirst { it.toolCallId == event.toolCallId && it.toolCallId != null }
                    val updatedCalls = if (existingIdx >= 0) {
                        msg.toolCalls.toMutableList().also { it[existingIdx] = record }
                    } else {
                        msg.toolCalls + record
                    }
                    msg.copy(
                        toolCalls = updatedCalls,
                        status = MessageStatus.EXECUTING,
                    )
                }
            }

            is AgentEvent.ToolResultEvent -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() ToolResultEvent toolName=${event.toolName} success=${event.result.success} outputLen=${event.result.output.length}")
                updateAssistantMessage(thinkingId) { msg ->
                    val updatedCalls = msg.toolCalls.map { call ->
                        if (call.toolName == event.toolName && call.output == null) {
                            call.copy(
                                output = event.result.output,
                                status = if (event.result.success) ToolCallStatus.COMPLETED
                                    else ToolCallStatus.FAILED,
                                completedAt = System.currentTimeMillis(),
                            )
                        } else call
                    }
                    msg.copy(toolCalls = updatedCalls)
                }
            }

            is AgentEvent.FileChanged -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() FileChanged count=${event.changes.size} paths=${event.changes.take(3).joinToString { it.filePath }}")
                refreshProjectFiles()
                // Reload skills if skill files changed
                if (event.changes.any { it.filePath.contains(".agent/skills/") || it.filePath.contains("skill.yaml") }) {
                    val skillsDir = java.io.File(fileOperations.projectRoot(), AppConstants.PROJECT_SKILLS_DIR)
                    skillManager.loadProjectSkills(skillsDir)
                }
                if (event.changes.isNotEmpty()) {
                    currentAssistantMsgId?.let { id ->
                        updateAssistantMessage(id) { msg ->
                            msg.copy(fileChanges = event.changes)
                        }
                    }
                }
            }

            is AgentEvent.ContextWarning -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() ContextWarning level=${event.level}")
                val sysMsg = ChatMessage(
                    role = MessageRole.SYSTEM,
                    content = when (event.level) {
                        com.nuclearboy.api.deepseek.ContextWarningLevel.YELLOW ->
                            "上下文空间稍显紧张，已自动整理早期对话"
                        com.nuclearboy.api.deepseek.ContextWarningLevel.RED ->
                            "上下文即将用完，已进行深度压缩"
                        else -> event.message
                    },
                    status = MessageStatus.COMPLETE,
                )
                _messages.update { it + sysMsg }
                _scrollToBottom.value++
            }

            is AgentEvent.TokenUpdate -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() TokenUpdate prompt=${event.usage.promptTokens} completion=${event.usage.completionTokens} total=${event.usage.totalTokens}")
                currentAssistantMsgId?.let { id ->
                    updateAssistantMessage(id) { msg ->
                        msg.copy(tokenUsage = event.usage)
                    }
                }
            }

            is AgentEvent.Error -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() Error message=${event.error.humanMessage}")
                val errorMsg = ChatMessage(
                    role = MessageRole.SYSTEM,
                    content = "处理时遇到了问题：${event.error.humanMessage}",
                    status = MessageStatus.ERROR,
                )
                _messages.update { it + errorMsg }
                _scrollToBottom.value++
            }

            is AgentEvent.Complete -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() Complete")
                /* Finalization handled in finally block */
            }

            is AgentEvent.Retrying -> {
                android.util.Log.e("NuclearBoy", "[ChatVM] handleAgentEvent() Retrying")
                updateAssistantMessage(thinkingId) { msg ->
                    msg.copy(status = MessageStatus.THINKING)
                }
            }
        }
    }

    private fun handleAgentError(error: Throwable, thinkingId: String) {
        val friendlyMsg = when (error) {
            is kotlinx.coroutines.CancellationException -> "已取消"
            else -> "出了一点小问题…${error.message?.take(100) ?: ""}"
        }
        // Update thinking placeholder with error
        updateAssistantMessage(thinkingId) { msg ->
            msg.copy(
                content = friendlyMsg,
                status = MessageStatus.ERROR,
            )
        }
        // Also update assistant message if one was created
        currentAssistantMsgId?.let { id ->
            updateAssistantMessage(id) { msg ->
                if (msg.content.isEmpty()) msg.copy(
                    content = friendlyMsg,
                    status = MessageStatus.ERROR,
                ) else msg
            }
        }
    }

    private fun finalizeProcessing(thinkingId: String) {
        _isProcessing.value = false
        agentJob = null
        // Mark thinking placeholder as COMPLETE
        updateAssistantMessage(thinkingId) { msg ->
            if (msg.status == MessageStatus.THINKING) msg.copy(status = MessageStatus.COMPLETE)
            else msg
        }
        // Finalize assistant message if one was created
        val streamState = _streamingState.value
        currentAssistantMsgId?.let { id ->
            val finalStatus = if (streamState?.responseText?.isEmpty() != false) "ERROR" else "COMPLETE"
            android.util.Log.e("NuclearBoy", "[ChatVM] finalizeProcessing() assistantId=$id finalStatus=$finalStatus responseTextLen=${streamState?.responseText?.length ?: 0}")
            updateAssistantMessage(id) { msg ->
                if (msg.content.isEmpty() && (streamState?.responseText?.isEmpty() != false)) {
                    msg.copy(content = "没能生成回复，请换个方式描述你的需求", status = MessageStatus.ERROR)
                } else if (msg.status == MessageStatus.STREAMING || msg.status == MessageStatus.THINKING) {
                    msg.copy(status = MessageStatus.COMPLETE)
                } else {
                    msg
                }
            }
        }
        _streamingState.value = null
        // Reset class-level tracking
        currentThinkingId = null
        currentAssistantMsgId = null
        // Show "ready" notification with content summary — find last ASSISTANT message
        val lastAssistant = _messages.value.findLast { it.role == MessageRole.ASSISTANT }
        val notificationSent = lastAssistant != null && lastAssistant.content.isNotBlank()
        android.util.Log.e("NuclearBoy", "[ChatVM] finalizeProcessing() notificationSent=$notificationSent lastAssistantRole=${lastAssistant?.role}")
        if (lastAssistant != null && lastAssistant.content.isNotBlank()) {
            notificationCallback?.invoke(lastAssistant.content, currentProjectId)
        }
        saveMessages()
        // 自动提取记忆：从本次对话中学习用户偏好和项目信息
        val projectId = currentProjectId ?: "default"
        val lastUser = lastUserMessage?.content ?: ""
        val lastAi = lastAssistant?.content ?: ""
        if (lastAi.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val r1 = memoryStore.updateUserProfile("last_project", projectId, "interaction", 0.9f)
                    android.util.Log.e("NuclearBoy", "[ChatVM] memoryWrite last_project=$projectId result=$r1")
                    val convResult = memoryStore.getProfileValue("total_conversations")
                    val convCount = if (convResult is AppResult.Success) (convResult.data?.toIntOrNull() ?: 0) + 1 else 1
                    val r2 = memoryStore.updateUserProfile("total_conversations", convCount.toString(), "interaction", 0.9f)
                    android.util.Log.e("NuclearBoy", "[ChatVM] memoryWrite total_conversations=$convCount result=$r2")
                    val r3 = memoryStore.autoExtractMemories(projectId, lastUser, lastAi)
                    android.util.Log.e("NuclearBoy", "[ChatVM] memoryWrite autoExtractMemories result=$r3")
                } catch (e: Exception) {
                    android.util.Log.e("NuclearBoy", "[ChatVM] memoryWrite FAILED: ${e.message}", e)
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun updateAssistantMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { messages ->
            messages.map { msg -> if (msg.id == id) transform(msg) else msg }
        }
    }
}
