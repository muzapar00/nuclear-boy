package com.nuclearboy.api.deepseek

import com.nuclearboy.common.AppConstants
import com.nuclearboy.common.ModelTier
import com.nuclearboy.common.SessionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real-time token usage and cost tracker.
 * Feeds the HUD and session stats panels.
 */
data class TokenSnapshot(
    val tokensPerSecond: Double = 0.0,
    val reasoningTokensPerSecond: Double = 0.0,
    val promptTokensTotal: Long = 0,
    val completionTokensTotal: Long = 0,
    val cachedTokensTotal: Long = 0,
    val reasoningTokensTotal: Long = 0,
    val promptTokensThisRequest: Long = 0,
    val completionTokensThisRequest: Long = 0,
    val cachedTokensThisRequest: Long = 0,
    val cacheHitRate: Double = 0.0,          // 0.0 - 1.0
    val contextUsed: Long = 0,
    val contextRemaining: Long = AppConstants.DEEPSEEK_CONTEXT_WINDOW,
    val contextUsagePercent: Double = 0.0,    // 0.0 - 1.0
    val estimatedCostUsd: Double = 0.0,
    val requestCount: Int = 0,
    val modelTier: ModelTier = ModelTier.V4_PRO,
    val thinkingMode: String = "disabled",
    val averageLatencyMs: Long = 0,
)

data class RequestRecord(
    val model: String,
    val thinkingMode: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val reasoningTokens: Long,
    val costUsd: Double,
    val latencyMs: Long,
    val tokensPerSecond: Double,
    val timestamp: Long = System.currentTimeMillis(),
)

class TokenTracker {

    private val _snapshot = MutableStateFlow(TokenSnapshot())
    val snapshot: StateFlow<TokenSnapshot> = _snapshot.asStateFlow()

    val requestHistory = CopyOnWriteArrayList<RequestRecord>()

    private var startTimeMs: Long = 0L
    private var lastUpdateTimeMs: Long = 0L
    private var tokensInCurrentStream: Long = 0

    fun startRequest(modelTier: ModelTier, thinkingMode: String, promptTokens: Long) {
        startTimeMs = System.currentTimeMillis()
        lastUpdateTimeMs = startTimeMs
        tokensInCurrentStream = 0
        android.util.Log.e("NuclearBoy", "[TokenTracker] startRequest() modelTier=$modelTier thinkingMode=$thinkingMode promptTokens=$promptTokens")

        _snapshot.update { current ->
            current.copy(
                modelTier = modelTier,
                thinkingMode = thinkingMode,
                promptTokensThisRequest = promptTokens,
                completionTokensThisRequest = 0,
                cachedTokensThisRequest = 0,
                tokensPerSecond = 0.0,
                reasoningTokensPerSecond = 0.0,
            )
        }
    }

    fun onStreamToken(isReasoning: Boolean = false) {
        val now = System.currentTimeMillis()
        tokensInCurrentStream++
        if (tokensInCurrentStream % 100 == 0L) {
            val tps = (1000.0 * tokensInCurrentStream / (now - startTimeMs + 1))
            android.util.Log.e("NuclearBoy", "[TokenTracker] onStreamToken() milestone token=$tokensInCurrentStream isReasoning=$isReasoning tps=${"%.1f".format(tps)}")
        }
        val elapsed = (now - lastUpdateTimeMs).coerceAtLeast(1)

        _snapshot.update { current ->
            val newCompletion = if (isReasoning) {
                current.completionTokensThisRequest
            } else {
                current.completionTokensThisRequest + 1
            }
            val newReasoning = if (isReasoning) {
                current.reasoningTokensThisRequest() + 1
            } else {
                current.reasoningTokensThisRequest()
            }

            current.copy(
                completionTokensThisRequest = current.completionTokensThisRequest + 1,
                tokensPerSecond = if (isReasoning) current.tokensPerSecond
                    else (1000.0 * tokensInCurrentStream / (now - startTimeMs + 1)),
                reasoningTokensPerSecond = if (isReasoning)
                    (1000.0 * tokensInCurrentStream / (now - startTimeMs + 1))
                    else current.reasoningTokensPerSecond,
            )
        }
        lastUpdateTimeMs = now
    }

    fun onRequestComplete(usage: UsageDto) {
        val now = System.currentTimeMillis()
        val latencyMs = now - startTimeMs
        val totalTokens = usage.completionTokens
        val speed = if (latencyMs > 0) (totalTokens * 1000.0 / latencyMs) else 0.0
        val cachedTokens = usage.promptTokensDetails?.cachedTokens ?: 0
        val hitRate = if (usage.promptTokens > 0) cachedTokens * 100.0 / usage.promptTokens else 0.0
        android.util.Log.e("NuclearBoy", "缓存: ${cachedTokens}/${usage.promptTokens} = ${hitRate.toInt()}%")
        val reasoningTokens = usage.completionTokensDetails?.reasoningTokens ?: 0
        val cost = calculateCost(
            modelTier = _snapshot.value.modelTier,
            promptTokens = usage.promptTokens,
            cachedTokens = cachedTokens,
            outputTokens = usage.completionTokens
        )
        android.util.Log.e("NuclearBoy", "[TokenTracker] onRequestComplete() prompt=${usage.promptTokens} completion=${usage.completionTokens} total=${usage.totalTokens} cached=$cachedTokens (${"%.1f".format(hitRate)}%) reasoning=$reasoningTokens cost=$${"%.6f".format(cost)} latency=${latencyMs}ms speed=${"%.1f".format(speed)} t/s")

        // Record for history
        requestHistory.add(
            RequestRecord(
                model = _snapshot.value.modelTier.modelId,
                thinkingMode = _snapshot.value.thinkingMode,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                cachedTokens = cachedTokens,
                reasoningTokens = reasoningTokens,
                costUsd = cost,
                latencyMs = latencyMs,
                tokensPerSecond = speed,
            )
        )

        _snapshot.update { current ->
            val newPromptTotal = current.promptTokensTotal + usage.promptTokens
            val newCacheTotal = current.cachedTokensTotal + cachedTokens
            // Show per-request cache rate (not cumulative) — more useful for UX
            val cacheRate = if (usage.promptTokens > 0) {
                cachedTokens.toDouble() / usage.promptTokens.toDouble()
            } else 0.0

            val newContextUsed = usage.promptTokens
            val newContextRemaining = (AppConstants.DEEPSEEK_CONTEXT_WINDOW - newContextUsed)
                .coerceAtLeast(0)

            current.copy(
                promptTokensTotal = newPromptTotal,
                completionTokensTotal = current.completionTokensTotal + usage.completionTokens,
                cachedTokensTotal = newCacheTotal,
                reasoningTokensTotal = current.reasoningTokensTotal + reasoningTokens,
                cacheHitRate = cacheRate,
                contextUsed = newContextUsed,
                contextRemaining = newContextRemaining,
                contextUsagePercent = newContextUsed.toDouble() / AppConstants.DEEPSEEK_CONTEXT_WINDOW,
                estimatedCostUsd = current.estimatedCostUsd + cost,
                tokensPerSecond = speed,
                requestCount = current.requestCount + 1,
                averageLatencyMs = ((current.averageLatencyMs * current.requestCount) + latencyMs)
                    .coerceAtLeast(0) / (current.requestCount + 1),
                completionTokensThisRequest = usage.completionTokens,
                cachedTokensThisRequest = cachedTokens,
            )
        }
    }

    fun getSessionStats(): SessionStats {
        val s = _snapshot.value
        android.util.Log.e("NuclearBoy", "[TokenTracker] getSessionStats() requests=${s.requestCount} promptTotal=${s.promptTokensTotal} completionTotal=${s.completionTokensTotal} cachedTotal=${s.cachedTokensTotal} reasoningTotal=${s.reasoningTokensTotal} costTotal=$${"%.6f".format(s.estimatedCostUsd)} avgSpeed=${"%.1f".format(s.tokensPerSecond)} t/s avgLatency=${s.averageLatencyMs}ms")
        return SessionStats(
            sessionId = "",
            startedAt = startTimeMs,
            requestCount = s.requestCount,
            totalPromptTokens = s.promptTokensTotal,
            totalCompletionTokens = s.completionTokensTotal,
            totalCachedTokens = s.cachedTokensTotal,
            totalReasoningTokens = s.reasoningTokensTotal,
            totalCostUsd = s.estimatedCostUsd,
            averageSpeed = s.tokensPerSecond,
            averageLatencyMs = s.averageLatencyMs,
        )
    }

    fun reset() {
        _snapshot.value = TokenSnapshot()
        requestHistory.clear()
    }

    private fun calculateCost(
        modelTier: ModelTier,
        promptTokens: Long,
        cachedTokens: Long,
        outputTokens: Long,
    ): Double {
        val uncachedPrompt = promptTokens - cachedTokens
        return when (modelTier) {
            ModelTier.V4_PRO -> {
                (uncachedPrompt / 1_000_000.0) * AppConstants.Pricing.PRO_INPUT_CACHE_MISS +
                        (cachedTokens / 1_000_000.0) * AppConstants.Pricing.PRO_INPUT_CACHE_HIT +
                        (outputTokens / 1_000_000.0) * AppConstants.Pricing.PRO_OUTPUT
            }
            ModelTier.V4_FLASH -> {
                (uncachedPrompt / 1_000_000.0) * AppConstants.Pricing.FLASH_INPUT_CACHE_MISS +
                        (cachedTokens / 1_000_000.0) * AppConstants.Pricing.FLASH_INPUT_CACHE_HIT +
                        (outputTokens / 1_000_000.0) * AppConstants.Pricing.FLASH_OUTPUT
            }
        }
    }

    private fun TokenSnapshot.reasoningTokensThisRequest(): Long {
        // Tracked via stream callback — simple count for now
        return this.reasoningTokensTotal
    }
}
