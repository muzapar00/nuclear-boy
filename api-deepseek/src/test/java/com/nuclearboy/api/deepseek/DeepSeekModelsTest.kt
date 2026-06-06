package com.nuclearboy.api.deepseek

import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class DeepSeekModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `ToolDefinitionDto serializes type field`() {
        val dto = ToolDefinitionDto(
            function = FunctionDefinitionDto(
                name = "test_tool",
                description = "A test tool",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("File path"))
                        })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("path")) })
                },
            )
        )
        val serialized = json.encodeToString(dto)
        // Must contain type: "function"
        assertTrue(serialized.contains("\"type\":\"function\""))
        // Must contain the function name
        assertTrue(serialized.contains("\"name\":\"test_tool\""))
        // parameters must be a JSON object, not a string
        assertTrue(serialized.contains("\"parameters\":{"))
    }

    @Test
    fun `ChatCompletionRequest includes tools when provided`() {
        val request = ChatCompletionRequest(
            model = "deepseek-v4-pro",
            messages = listOf(MessageDto(role = "user", content = "Hi")),
            tools = listOf(
                ToolDefinitionDto(
                    function = FunctionDefinitionDto(name = "test", description = "Test"),
                )
            ),
        )
        val serialized = json.encodeToString(request)
        assertTrue(serialized.contains("\"tools\":"))
        assertTrue(serialized.contains("\"type\":\"function\""))
    }

    @Test
    fun `MessageDto strips reasoningContent from assistant messages`() {
        val msg = MessageDto(
            role = "assistant",
            content = "Hello",
            reasoningContent = "I should say hello",
        )
        // After sanitization, reasoning should be null
        val sanitized = msg.copy(reasoningContent = null)
        assertNull(sanitized.reasoningContent)
        assertEquals("Hello", sanitized.content)
    }

    @Test
    fun `StreamChunk parsing handles content delta`() {
        val chunkJson = """{"choices":[{"index":0,"delta":{"content":"Hello world"}}]}"""
        val chunk = json.decodeFromString<StreamChunk>(chunkJson)
        assertEquals(1, chunk.choices.size)
        assertEquals("Hello world", chunk.choices[0].delta?.content)
    }

    @Test
    fun `StreamChunk parsing handles tool call delta`() {
        val chunkJson = """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"read_file","arguments":"{}"}}]}}]}"""
        val chunk = json.decodeFromString<StreamChunk>(chunkJson)
        assertEquals(1, chunk.choices.size)
        assertNotNull(chunk.choices[0].delta?.toolCalls)
    }

    @Test
    fun `UsageDto parsing handles cached tokens`() {
        val usageJson = """{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150,"prompt_tokens_details":{"cached_tokens":80}}"""
        val usage = json.decodeFromString<UsageDto>(usageJson)
        assertEquals(100, usage.promptTokens)
        assertEquals(80, usage.promptTokensDetails?.cachedTokens)
    }
}
