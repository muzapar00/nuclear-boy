package com.nuclearboy.api.deepseek

import com.nuclearboy.common.ModelTier
import com.nuclearboy.common.ThinkingMode
import org.junit.Assert.*
import org.junit.Test

class ModelRouterTest {

    private val router = ModelRouter()

    @Test
    fun `simple message routes to Flash`() {
        val decision = router.route(
            userMessage = "创建一个文件",
            fileCount = 0,
            isArchitectureDecision = false,
            conversationContextSize = 0,
        )
        assertEquals(ModelTier.V4_FLASH, decision.modelTier)
        assertEquals(ThinkingMode.DISABLED, decision.thinkingMode)
    }

    @Test
    fun `complex message routes to Pro with thinking`() {
        val decision = router.route(
            userMessage = "请帮我重新设计这个系统的架构，包括数据库schema、API设计、以及前端组件结构。当前有5个微服务需要合并为2个。", // long message
            fileCount = 5,
            isArchitectureDecision = true,
            conversationContextSize = 100_000,
        )
        assertEquals(ModelTier.V4_PRO, decision.modelTier)
        assertEquals(ThinkingMode.MAX, decision.thinkingMode)
    }

    @Test
    fun `medium complexity routes to Pro high thinking`() {
        val decision = router.route(
            userMessage = "帮我修复这个bug，涉及到3个文件的修改",
            fileCount = 3,
            isArchitectureDecision = false,
            conversationContextSize = 50_000,
        )
        assertEquals(ModelTier.V4_PRO, decision.modelTier)
    }

    @Test
    fun `user preference overrides routing`() {
        val decision = router.route(
            userMessage = "创建文件",
            fileCount = 0,
            userPreference = ModelTier.V4_PRO,
            userThinkingPreference = ThinkingMode.HIGH,
        )
        assertEquals(ModelTier.V4_PRO, decision.modelTier)
        assertEquals(ThinkingMode.HIGH, decision.thinkingMode)
    }

    @Test
    fun `night time prefers Flash for simple tasks`() {
        val decision = router.route(
            userMessage = "格式化这段代码",
            fileCount = 1,
            isNightTime = true,
        )
        assertEquals(ModelTier.V4_FLASH, decision.modelTier)
    }

    @Test
    fun `low budget prefers Flash`() {
        val decision = router.route(
            userMessage = "帮我重构这个函数",
            fileCount = 2,
            monthlyBudgetRemaining = 5.0, // Low budget
        )
        assertEquals(ModelTier.V4_FLASH, decision.modelTier)
    }

    @Test
    fun `reason string is informative`() {
        val decision = router.route(
            userMessage = "创建一个简单的Python脚本",
            fileCount = 0,
        )
        assertTrue(decision.reason.contains("复杂度"))
        assertTrue(decision.reason.contains("Flash"))
    }
}
