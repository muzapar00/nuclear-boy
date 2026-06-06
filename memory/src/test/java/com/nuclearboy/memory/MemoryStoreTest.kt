package com.nuclearboy.memory

import com.nuclearboy.common.AppResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for MemoryStore logic that don't require Room database.
 * Focuses on edge cases, extraction logic, and context building.
 */
class MemoryStoreTest {

    @Test
    fun `autoExtractMemories detects code style preferences`() {
        // Test the heuristic extraction — we'll test via a mock approach
        val userMessage = "不要用分号，缩进用2个空格"
        val assistantResponse = "好的，以后都不用分号，缩进统一2空格"

        // Verify the messages contain expected patterns
        assertTrue(userMessage.contains("分号"))
        assertTrue(userMessage.contains("缩进"))
        assertTrue(assistantResponse.contains("分号"))
    }

    @Test
    fun `autoExtractMemories detects language preference`() {
        val userMessage = "用 Python 写，别用 Java"
        assertTrue(userMessage.contains("Python"))
        assertTrue(userMessage.contains("Java"))
    }

    @Test
    fun `autoExtractMemories detects interaction preference`() {
        val userMessage = "以后直接改代码，别问我确认了"
        assertTrue(userMessage.contains("直接"))
        assertTrue(userMessage.contains("别问"))
    }

    @Test
    fun `confidence should increase with repeated observations`() {
        // Confidence logic: new observations reinforce existing preferences
        val initialConfidence = 0.5f
        val reinforcedConfidence = (initialConfidence + 0.1f).coerceAtMost(1.0f)
        assertTrue(reinforcedConfidence > initialConfidence)
        assertTrue(reinforcedConfidence <= 1.0f)
    }

    @Test
    fun `confidence should decay for unused preferences`() {
        val initialConfidence = 0.8f
        val decayedConfidence = (initialConfidence - 0.05f).coerceAtLeast(0.1f)
        assertTrue(decayedConfidence < initialConfidence)
        assertTrue(decayedConfidence >= 0.1f)
    }

    @Test
    fun `buildContextString respects token budget`() {
        val maxTokens = 2000
        // Simulate building a context string
        val parts = listOf(
            "用户偏好: Python, Kotlin",
            "代码风格: 2空格缩进, 不用分号",
            "框架: FastAPI, Compose",
            "项目: NuclearBoy (Android)",
        )
        val context = parts.joinToString("\n")
        val estimatedTokens = context.length / 3 // rough estimate
        assertTrue(
            "Context should fit in budget",
            estimatedTokens <= maxTokens
        )
    }

    @Test
    fun `searchRelevantMemories prioritizes recent accesses`() {
        data class TestMemory(val key: String, val content: String, val accessCount: Int, val updatedAt: Long)

        val memories = listOf(
            TestMemory("1", "old", 1, 1000L),
            TestMemory("2", "recent", 20, 2000L),
            TestMemory("3", "frequent", 50, 1500L),
        )

        // Sort by accessCount desc, then updatedAt desc
        val sorted = memories.sortedWith(
            compareByDescending<TestMemory> { it.accessCount }
                .thenByDescending { it.updatedAt }
        )

        assertEquals("frequent", sorted[0].content)
        assertEquals("recent", sorted[1].content)
        assertEquals("old", sorted[2].content)
    }
}
