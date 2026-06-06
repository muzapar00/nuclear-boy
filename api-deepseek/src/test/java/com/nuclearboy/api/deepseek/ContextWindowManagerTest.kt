package com.nuclearboy.api.deepseek

import com.nuclearboy.common.AppConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContextWindowManagerTest {

    private lateinit var manager: ContextWindowManager

    @Before
    fun setUp() {
        manager = ContextWindowManager()
    }

    @Test
    fun `initial budget is zero`() {
        val budget = manager.budget.value
        assertEquals(0L, budget.totalUsed)
        assertEquals(AppConstants.DEEPSEEK_CONTEXT_WINDOW, budget.remaining)
        assertEquals(ContextWarningLevel.OK, budget.warningLevel)
    }

    @Test
    fun `update allocation reflects in budget`() {
        manager.updateAllocation(systemPrompt = 3000)
        assertEquals(3000, manager.budget.value.systemPrompt)
        assertEquals(3000, manager.budget.value.totalUsed)
    }

    @Test
    fun `canFit returns correct answer`() {
        manager.updateAllocation(conversationHistory = 900_000)
        assertTrue(manager.canFit(50_000))
        assertFalse(manager.canFit(200_000))
    }

    @Test
    fun `yellow warning at 80 percent`() {
        manager.updateAllocation(conversationHistory = AppConstants.CONTEXT_WARNING_YELLOW)
        assertTrue(manager.needsCompression())
        assertEquals(ContextWarningLevel.YELLOW, manager.budget.value.warningLevel)
    }

    @Test
    fun `red warning at 95 percent`() {
        manager.updateAllocation(conversationHistory = AppConstants.CONTEXT_WARNING_RED)
        assertTrue(manager.needsUrgentCompression())
        assertEquals(ContextWarningLevel.RED, manager.budget.value.warningLevel)
    }

    @Test
    fun `compress conversation reduces budget`() {
        manager.updateAllocation(conversationHistory = 250_000)
        val result = manager.compressConversation(20)

        assertTrue(result.tokensSaved > 0)
        assertTrue(manager.budget.value.conversationHistory < 250_000)
        assertTrue(result.message.contains("压缩"))
    }

    @Test
    fun `compress does nothing if under threshold`() {
        manager.updateAllocation(conversationHistory = 50_000)
        val result = manager.compressConversation(10)

        assertEquals(0, result.tokensSaved)
        assertEquals(50_000, manager.budget.value.conversationHistory)
    }

    @Test
    fun `emergency compress aggressively reduces`() {
        manager.updateAllocation(
            conversationHistory = 400_000,
            attachedFiles = 300_000,
            projectContext = 200_000,
        )
        val before = manager.budget.value.totalUsed
        val result = manager.emergencyCompress()

        assertTrue(result.tokensSaved > 0)
        assertTrue(manager.budget.value.totalUsed < before)
    }

    @Test
    fun `estimate tokens is reasonable`() {
        val tokens = manager.estimateTokens("Hello, world!")
        assertTrue(tokens in 1..10)
    }

    @Test
    fun `estimate tokens for Chinese text`() {
        val tokens = manager.estimateTokens("你好世界这是一段中文文本")
        assertTrue(tokens > 0)
    }

    @Test
    fun `reset clears all state`() {
        manager.updateAllocation(systemPrompt = 3000, conversationHistory = 100_000)
        manager.reset()

        val budget = manager.budget.value
        assertEquals(0L, budget.totalUsed)
        assertEquals(ContextWarningLevel.OK, budget.warningLevel)
    }
}
