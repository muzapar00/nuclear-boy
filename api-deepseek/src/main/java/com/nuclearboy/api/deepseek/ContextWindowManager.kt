package com.nuclearboy.api.deepseek

import com.nuclearboy.common.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the 1M token context window budget.
 * Tracks allocations and triggers compression when needed.
 */
data class ContextBudget(
    val systemPrompt: Long = 0,
    val userProfile: Long = 0,
    val projectContext: Long = 0,
    val conversationHistory: Long = 0,
    val toolDefinitions: Long = 0,
    val attachedFiles: Long = 0,
) {
    val totalUsed: Long
        get() = systemPrompt + userProfile + projectContext +
                conversationHistory + toolDefinitions + attachedFiles

    val remaining: Long
        get() = (AppConstants.DEEPSEEK_CONTEXT_WINDOW - totalUsed).coerceAtLeast(0)

    val usagePercent: Double
        get() = totalUsed.toDouble() / AppConstants.DEEPSEEK_CONTEXT_WINDOW

    val warningLevel: ContextWarningLevel
        get() = when {
            totalUsed >= AppConstants.CONTEXT_WARNING_RED -> ContextWarningLevel.RED
            totalUsed >= AppConstants.CONTEXT_WARNING_YELLOW -> ContextWarningLevel.YELLOW
            totalUsed >= AppConstants.DEEPSEEK_CONTEXT_WINDOW * 0.3 -> ContextWarningLevel.GREEN
            else -> ContextWarningLevel.OK
        }
}

enum class ContextWarningLevel(val label: String, val colorHex: Long) {
    OK("正常", 0xFF4CAF50),
    GREEN("良好", 0xFF4CAF50),
    YELLOW("注意", 0xFFFFC107),
    RED("危险", 0xFFFF5252),
}

data class CompressionResult(
    val tokensSaved: Long,
    val newBudget: ContextBudget,
    val message: String,
)

class ContextWindowManager {

    private val _budget = MutableStateFlow(ContextBudget())
    val budget: StateFlow<ContextBudget> = _budget.asStateFlow()

    private var conversationTurnCount = 0
    private val conversationSummaries = mutableListOf<String>()

    /**
     * Check if there's enough room in the context window for additional tokens.
     */
    fun canFit(additionalTokens: Long): Boolean {
        return _budget.value.remaining >= additionalTokens
    }

    /**
     * Check if compression is needed based on current budget.
     */
    fun needsCompression(): Boolean {
        return _budget.value.totalUsed >= AppConstants.CONTEXT_WARNING_YELLOW
    }

    /**
     * Check if compression is urgent (red zone).
     */
    fun needsUrgentCompression(): Boolean {
        return _budget.value.totalUsed >= AppConstants.CONTEXT_WARNING_RED
    }

    /**
     * Update budget allocation for a specific category.
     */
    fun updateAllocation(
        systemPrompt: Long? = null,
        userProfile: Long? = null,
        projectContext: Long? = null,
        conversationHistory: Long? = null,
        toolDefinitions: Long? = null,
        attachedFiles: Long? = null,
    ) {
        _budget.value = _budget.value.copy(
            systemPrompt = systemPrompt ?: _budget.value.systemPrompt,
            userProfile = userProfile ?: _budget.value.userProfile,
            projectContext = projectContext ?: _budget.value.projectContext,
            conversationHistory = conversationHistory ?: _budget.value.conversationHistory,
            toolDefinitions = toolDefinitions ?: _budget.value.toolDefinitions,
            attachedFiles = attachedFiles ?: _budget.value.attachedFiles,
        )
    }

    /**
     * Compress conversation history by summarizing early turns.
     * Returns the result of compression.
     */
    fun compressConversation(turnCount: Int): CompressionResult {
        val current = _budget.value

        // If conversation history is under threshold, no compression needed
        if (current.conversationHistory < AppConstants.CONVERSATION_COMPRESS_THRESHOLD) {
            return CompressionResult(
                tokensSaved = 0,
                newBudget = current,
                message = "上下文还很充裕，不需要压缩"
            )
        }

        // Summarize roughly half of the conversation tokens
        val targetReduction = current.conversationHistory / 2
        val newConversationTokens = current.conversationHistory - targetReduction

        // Generate a summary (in production, this would call the LLM for summarization)
        val summary = "（较早的对话已压缩：包含 ${turnCount / 2} 轮对话的关键信息）"
        conversationSummaries.add(summary)

        val newBudget = current.copy(
            conversationHistory = newConversationTokens.coerceAtLeast(50_000),
        )

        _budget.value = newBudget

        return CompressionResult(
            tokensSaved = targetReduction,
            newBudget = newBudget,
            message = "我帮你整理了一下前面的对话，节省了 ${targetReduction / 1000}K tokens 的上下文空间 ✨"
        )
    }

    /**
     * Force-aggressive compression when context is critically full.
     */
    fun emergencyCompress(): CompressionResult {
        val current = _budget.value
        val targetReduction = current.totalUsed - AppConstants.DEEPSEEK_CONTEXT_WINDOW / 2

        val newBudget = current.copy(
            conversationHistory = (current.conversationHistory * 0.3).toLong().coerceAtLeast(10_000),
            attachedFiles = (current.attachedFiles * 0.5).toLong(),
            projectContext = (current.projectContext * 0.7).toLong(),
        )

        _budget.value = newBudget

        return CompressionResult(
            tokensSaved = targetReduction,
            newBudget = newBudget,
            message = "上下文快满了，我做了一次深度压缩，释放了约 ${targetReduction / 1000}K tokens。早期对话和部分文件内容被精简了。"
        )
    }

    /**
     * Estimate token count for a string. Rough heuristic: ~4 chars per token.
     */
    fun estimateTokens(text: String): Long {
        // GPT/DeepSeek tokenizer averages ~3-4 characters per token for Chinese+English mixed text
        return (text.length / 3.5).toLong().coerceAtLeast(1)
    }

    /**
     * Reset the context window for a new conversation.
     */
    fun reset() {
        _budget.value = ContextBudget()
        conversationTurnCount = 0
        conversationSummaries.clear()
    }

    fun incrementTurn() {
        conversationTurnCount++
    }

    fun getConversationSummaries(): List<String> = conversationSummaries.toList()
}
