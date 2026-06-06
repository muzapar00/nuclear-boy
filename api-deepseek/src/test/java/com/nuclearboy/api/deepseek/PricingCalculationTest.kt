package com.nuclearboy.api.deepseek

import com.nuclearboy.common.AppConstants
import com.nuclearboy.common.ModelTier
import org.junit.Assert.*
import org.junit.Test

/**
 * Critical: Verifies token cost calculations are correct.
 * Pricing errors could mislead users about their spending.
 */
class PricingCalculationTest {

    @Test
    fun `V4 Pro cost with no cache hits`() {
        val tracker = TokenTracker()
        val usage = UsageDto(
            promptTokens = 100_000,
            completionTokens = 10_000,
            totalTokens = 110_000,
            promptTokensDetails = PromptTokensDetailsDto(cachedTokens = 0),
            completionTokensDetails = CompletionTokensDetailsDto(reasoningTokens = 0),
        )
        tracker.startRequest(ModelTier.V4_PRO, "disabled", 100_000)
        tracker.onRequestComplete(usage)

        val snapshot = tracker.snapshot.value
        // 100K prompt * $0.435/1M + 10K output * $0.87/1M
        val expected = (100_000.0 / 1_000_000) * 0.435 + (10_000.0 / 1_000_000) * 0.87
        assertEquals(expected, snapshot.estimatedCostUsd, 0.0001)
    }

    @Test
    fun `V4 Pro cost with cache hits is dramatically cheaper`() {
        val tracker = TokenTracker()
        val usage = UsageDto(
            promptTokens = 100_000,
            completionTokens = 10_000,
            totalTokens = 110_000,
            promptTokensDetails = PromptTokensDetailsDto(cachedTokens = 80_000),
            completionTokensDetails = CompletionTokensDetailsDto(reasoningTokens = 0),
        )
        tracker.startRequest(ModelTier.V4_PRO, "disabled", 100_000)
        tracker.onRequestComplete(usage)

        val snapshot = tracker.snapshot.value
        // 20K uncached * $0.435/1M + 80K cached * $0.003625/1M + 10K output * $0.87/1M
        val expected = (20_000.0 / 1_000_000) * 0.435 +
                (80_000.0 / 1_000_000) * 0.003625 +
                (10_000.0 / 1_000_000) * 0.87
        assertEquals(expected, snapshot.estimatedCostUsd, 0.0001)
        // Cached version should be much cheaper
        assertTrue(snapshot.estimatedCostUsd < 0.02)
    }

    @Test
    fun `V4 Flash cost is cheaper than Pro`() {
        val trackerPro = TokenTracker()
        val trackerFlash = TokenTracker()

        val usage = UsageDto(
            promptTokens = 100_000,
            completionTokens = 10_000,
            totalTokens = 110_000,
            promptTokensDetails = PromptTokensDetailsDto(cachedTokens = 0),
            completionTokensDetails = CompletionTokensDetailsDto(reasoningTokens = 0),
        )

        trackerPro.startRequest(ModelTier.V4_PRO, "disabled", 100_000)
        trackerPro.onRequestComplete(usage)

        trackerFlash.startRequest(ModelTier.V4_FLASH, "disabled", 100_000)
        trackerFlash.onRequestComplete(usage)

        assertTrue(
            "Flash should be cheaper than Pro",
            trackerFlash.snapshot.value.estimatedCostUsd <
                    trackerPro.snapshot.value.estimatedCostUsd
        )
    }

    @Test
    fun `cache hit rate is calculated correctly`() {
        val tracker = TokenTracker()

        // Request 1: 50% cache hit
        val usage1 = UsageDto(
            promptTokens = 100_000,
            completionTokens = 5_000,
            totalTokens = 105_000,
            promptTokensDetails = PromptTokensDetailsDto(cachedTokens = 50_000),
            completionTokensDetails = CompletionTokensDetailsDto(reasoningTokens = 0),
        )
        tracker.startRequest(ModelTier.V4_PRO, "disabled", 100_000)
        tracker.onRequestComplete(usage1)

        // Request 2: 100% cache hit
        val usage2 = UsageDto(
            promptTokens = 100_000,
            completionTokens = 5_000,
            totalTokens = 105_000,
            promptTokensDetails = PromptTokensDetailsDto(cachedTokens = 100_000),
            completionTokensDetails = CompletionTokensDetailsDto(reasoningTokens = 0),
        )
        tracker.startRequest(ModelTier.V4_PRO, "disabled", 100_000)
        tracker.onRequestComplete(usage2)

        val snapshot = tracker.snapshot.value
        // Total: 200K prompt, 150K cached → 75% hit rate
        assertEquals(0.75, snapshot.cacheHitRate, 0.01)
    }

    @Test
    fun `zero tokens has zero cost`() {
        val tracker = TokenTracker()
        val usage = UsageDto(
            promptTokens = 0,
            completionTokens = 0,
            totalTokens = 0,
        )
        tracker.startRequest(ModelTier.V4_PRO, "disabled", 0)
        tracker.onRequestComplete(usage)

        assertEquals(0.0, tracker.snapshot.value.estimatedCostUsd, 0.0)
    }

    @Test
    fun `context remaining is accurate`() {
        val tracker = TokenTracker()
        val usage = UsageDto(
            promptTokens = 500_000,
            completionTokens = 10_000,
            totalTokens = 510_000,
        )
        tracker.startRequest(ModelTier.V4_PRO, "disabled", 500_000)
        tracker.onRequestComplete(usage)

        assertEquals(500_000, tracker.snapshot.value.contextRemaining)
        assertEquals(0.5, tracker.snapshot.value.contextUsagePercent, 0.01)
    }
}
