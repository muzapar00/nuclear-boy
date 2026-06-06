package com.nuclearboy.common

import java.text.NumberFormat
import java.util.Locale

/**
 * App-wide constants. All magic numbers live here.
 */
object AppConstants {

    /** DeepSeek API base URL */
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"

    /** DeepSeek V4 context window size */
    const val DEEPSEEK_CONTEXT_WINDOW = 1_000_000L

    /** DeepSeek V4 max output tokens */
    const val DEEPSEEK_MAX_OUTPUT = 384_000L

    /** Context budget allocations (in tokens) */
    const val BUDGET_SYSTEM_PROMPT = 6_000L
    const val BUDGET_USER_PROFILE = 2_000L
    const val BUDGET_PROJECT_CONTEXT = 50_000L
    const val BUDGET_CONVERSATION_HISTORY = 100_000L
    const val BUDGET_TOOL_DEFINITIONS = 5_000L
    const val BUDGET_ATTACHED_FILES = 200_000L

    /** Context warning thresholds */
    const val CONTEXT_WARNING_YELLOW = 800_000L  // 80%
    const val CONTEXT_WARNING_RED = 950_000L     // 95%
    const val CONTEXT_FORCE_COMPRESS = 980_000L  // 98%

    /** Conversation limits before compression */
    const val CONVERSATION_COMPRESS_THRESHOLD = 200_000L
    const val FILE_CONTENT_TRUNCATE_THRESHOLD = 300_000L
    const val CRITICAL_REMAINING_TOKENS = 100_000L
    const val EMERGENCY_REMAINING_TOKENS = 50_000L

    /** API retry configuration */
    const val MAX_RETRIES = 3
    const val RETRY_BASE_DELAY_MS = 1000L
    const val MAX_CONCURRENT_REQUESTS = 10
    const val REQUEST_TIMEOUT_SECONDS = 120L

    /** Python sandbox */
    const val PYTHON_EXECUTION_TIMEOUT_SECONDS = 120L
    const val PYTHON_MAX_OUTPUT_CHARS = 100_000

    /** Pricing per 1M tokens (USD, post-May-2026 permanent pricing) */
    object Pricing {
        // V4 Flash
        const val FLASH_INPUT_CACHE_MISS = 0.14
        const val FLASH_INPUT_CACHE_HIT = 0.0028
        const val FLASH_OUTPUT = 0.28

        // V4 Pro (after 75% permanent cut)
        const val PRO_INPUT_CACHE_MISS = 0.435
        const val PRO_INPUT_CACHE_HIT = 0.003625
        const val PRO_OUTPUT = 0.87
    }

    /** User state sensing */
    const val NIGHT_MODE_START_HOUR = 22
    const val NIGHT_MODE_END_HOUR = 6
    const val FATIGUE_WARNING_MINUTES = 90
    const val LOW_BATTERY_THRESHOLD = 15
    const val REPEATED_CORRECTION_THRESHOLD = 3

    /** Project / file paths */
    const val APP_DOCUMENTS_DIR = "NuclearBoy"
    const val PROJECT_MEMORY_DIR = ".agent/memory"
    const val SANDBOX_WORKSPACE = "workspace"
    const val SKILLS_INSTALL_DIR = ".skills"
    const val PROJECT_SKILLS_DIR = ".agent/skills"

    /** UI */
    const val MIN_TOUCH_TARGET_DP = 48
    const val MIN_FONT_SIZE_SP = 14
    const val CHAT_WORKSPACE_SPLIT_RATIO = 0.6f

    /**
     * Format USD to Chinese Yuan (approximate, for display).
     * Uses a fixed conversion rate for consistency.
     */
    fun usdToCny(usd: Double): String {
        val cny = usd * 7.2
        return if (cny < 0.01) {
            "< ¥0.01"
        } else {
            "¥${String.format("%.2f", cny)}"
        }
    }

    fun formatTokens(tokens: Long): String {
        return when {
            tokens < 1000 -> "$tokens"
            tokens < 1_000_000 -> "${tokens / 1000}K"
            else -> "${tokens / 1_000_000}.${(tokens % 1_000_000) / 100_000}M"
        }
    }

    fun formatSpeed(tokensPerSecond: Double): String {
        return "${tokensPerSecond.toInt()} tok/s"
    }

    fun formatPercentage(value: Double): String {
        return "${(value * 100).toInt()}%"
    }
}
