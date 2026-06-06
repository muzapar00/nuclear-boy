package com.nuclearboy.api.deepseek

import com.nuclearboy.common.*

/**
 * Intelligent model router based on task complexity.
 * Automatically selects the best model and thinking mode for each request.
 */
class ModelRouter(
    private val defaultModel: ModelTier = ModelTier.V4_PRO,
    private val defaultThinking: ThinkingMode = ThinkingMode.DISABLED,
) {

    /**
     * Evaluate task complexity and return the optimal model + thinking mode.
     */
    fun route(
        userMessage: String,
        fileCount: Int = 0,
        isArchitectureDecision: Boolean = false,
        conversationContextSize: Long = 0,
        userPreference: ModelTier? = null,
        userThinkingPreference: ThinkingMode? = null,
        isNightTime: Boolean = false,
        monthlyBudgetRemaining: Double = Double.MAX_VALUE,
    ): RouteDecision {
        // User explicit override always wins
        if (userPreference != null && userThinkingPreference != null) {
            return RouteDecision(userPreference, userThinkingPreference, "使用你的偏好设置")
        }

        // Compute complexity score (0-10)
        val complexity = ComplexityEvaluator.evaluate(
            userMessage = userMessage,
            fileCount = fileCount,
            isArchitecture = isArchitectureDecision,
            conversationSize = conversationContextSize,
        )

        val (model, thinking) = when {
            complexity >= 8 -> ModelTier.V4_PRO to ThinkingMode.MAX
            complexity >= 5 -> ModelTier.V4_PRO to ThinkingMode.HIGH
            complexity >= 3 -> ModelTier.V4_FLASH to ThinkingMode.DISABLED  // 中等任务用 Flash
            else -> ModelTier.V4_FLASH to ThinkingMode.DISABLED             // 简单任务
        }

        // Budget-conscious: if running low, prefer Flash
        val finalModel = if (monthlyBudgetRemaining < 10.0 && complexity < 5) {
            ModelTier.V4_FLASH
        } else {
            userPreference ?: model
        }

        // Night time: prefer Flash for cost savings
        val nightAdjustedModel = if (isNightTime && complexity < 5) {
            ModelTier.V4_FLASH
        } else {
            finalModel
        }

        val reason = buildReason(complexity, nightAdjustedModel, thinking, isNightTime)

        return RouteDecision(nightAdjustedModel, userThinkingPreference ?: thinking, reason)
    }

    private fun buildReason(
        complexity: Int,
        model: ModelTier,
        thinking: ThinkingMode,
        isNight: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        parts.add("复杂度: ${complexity}/10")
        parts.add("模型: ${model.displayName}")
        if (thinking != ThinkingMode.DISABLED) {
            parts.add("思考: ${thinking.displayName}")
        }
        if (isNight) {
            parts.add("🌙 夜间省电模式")
        }
        return parts.joinToString(" · ")
    }
}

data class RouteDecision(
    val modelTier: ModelTier,
    val thinkingMode: ThinkingMode,
    val reason: String,
)

/**
 * Heuristic complexity evaluator.
 * Scores tasks from 0 (trivial) to 10 (extremely complex).
 */
private object ComplexityEvaluator {

    fun evaluate(
        userMessage: String,
        fileCount: Int,
        isArchitecture: Boolean,
        conversationSize: Long,
    ): Int {
        val messageLength = userMessage.length
        var score = 0

        // Message length (0-3 points)
        score += when {
            messageLength < 50 -> 0
            messageLength < 300 -> 1
            messageLength < 1000 -> 2
            else -> 3
        }

        // File count (0-2 points) — only matters for longer messages
        if (messageLength >= 50) {
            score += when {
                fileCount <= 3 -> 0
                fileCount in 4..10 -> 1
                else -> 2
            }
        }

        // Architecture decision (0-2 points)
        if (isArchitecture) score += 2

        // Conversation context size (0-2 points)
        score += when {
            conversationSize < 50_000 -> 0
            conversationSize < 150_000 -> 1
            else -> 2
        }

        // Keyword-based complexity (0-2 points)
        val complexityKeywords = listOf("架构", "设计", "重构", "分析", "优化", "debug", "调试",
            "architecture", "design", "refactor", "analyze", "optimize", "review", "审查")
        if (messageLength > 30 && complexityKeywords.any { it in userMessage.lowercase() }) {
            score += 1
        }

        // Keywords bonus
        // (handled by isArchitecture flag from agent layer)

        return score.coerceIn(0, 10)
    }
}
