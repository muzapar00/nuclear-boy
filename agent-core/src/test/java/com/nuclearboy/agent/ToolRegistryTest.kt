package com.nuclearboy.agent

import com.nuclearboy.common.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun `register and retrieve tool`() = runBlocking {
        val tool = ToolDefinition(
            name = "test_tool",
            description = "A test tool",
            parameters = listOf(
                ToolParameter("input", "string", "Test input", required = true),
            ),
            executor = { ToolResult(success = true, output = "OK") },
        )

        registry.register(tool)
        val retrieved = registry.getTool("test_tool")
        assertNotNull(retrieved)
        assertEquals("test_tool", retrieved?.name)
    }

    @Test
    fun `execute tool returns result`() = runBlocking {
        val tool = ToolDefinition(
            name = "greet",
            description = "Greet someone",
            parameters = listOf(
                ToolParameter("name", "string", "Name to greet", required = true),
            ),
            executor = { params ->
                ToolResult(success = true, output = "Hello, ${params["name"]}!")
            },
        )

        registry.register(tool)
        val result = registry.execute("greet", mapOf("name" to "World"))
        assertTrue(result.isSuccess)
        val toolResult = (result as AppResult.Success).data
        assertEquals("Hello, World!", toolResult.output)
    }

    @Test
    fun `execute nonexistent tool fails`() = runBlocking {
        val result = registry.execute("nonexistent", emptyMap())
        assertTrue(result.isFailure)
    }

    @Test
    fun `unregister removes tool`() = runBlocking {
        val tool = ToolDefinition(name = "temp", description = "", executor = { ToolResult(success = true, output = "") })
        registry.register(tool)
        assertNotNull(registry.getTool("temp"))

        registry.unregister("temp")
        assertNull(registry.getTool("temp"))
    }

    @Test
    fun `registerAll adds multiple tools`() = runBlocking {
        val tools = listOf(
            ToolDefinition(name = "a", description = "A", executor = { ToolResult(success = true, output = "") }),
            ToolDefinition(name = "b", description = "B", executor = { ToolResult(success = true, output = "") }),
            ToolDefinition(name = "c", description = "C", executor = { ToolResult(success = true, output = "") }),
        )
        registry.registerAll(tools)
        assertEquals(3, registry.toolCount())
    }

    @Test
    fun `toDeepSeekToolDefinitions produces valid schema`() = runBlocking {
        val tool = ToolDefinition(
            name = "read_file",
            description = "Read a file",
            parameters = listOf(
                ToolParameter("path", "string", "File path", required = true),
            ),
            executor = { ToolResult(success = true, output = "") },
        )
        registry.register(tool)

        val dtos = registry.toDeepSeekToolDefinitions()
        assertEquals(1, dtos.size)
        assertEquals("read_file", dtos[0].function.name)
        assertEquals("Read a file", dtos[0].function.description)
    }

    @Test
    fun `execute tool with missing required param fails gracefully`() = runBlocking {
        var receivedParams: Map<String, String>? = null
        val tool = ToolDefinition(
            name = "needs_param",
            description = "Needs a param",
            parameters = listOf(
                ToolParameter("required_field", "string", "Required", required = true),
            ),
            executor = { params ->
                receivedParams = params
                ToolResult(success = true, output = "Got: ${params["required_field"]}")
            },
        )

        registry.register(tool)
        // Call without the required parameter — the executor still runs,
        // it's up to the tool to validate
        val result = registry.execute("needs_param", emptyMap())
        assertTrue(result.isSuccess) // executor still ran
        val data = (result as AppResult.Success).data
        assertTrue(data.output.contains("null") || data.output == "Got: null")
    }

    @Test
    fun `skills executor callback works`() = runBlocking {
        var calledWithName = ""
        var calledWithParams: Map<String, String> = emptyMap()

        registry.skillsExecutor = { name, params ->
            calledWithName = name
            calledWithParams = params
            ToolResult(success = true, output = "Skill executed")
        }

        val result = registry.skillsExecutor?.invoke("my-skill", mapOf("key" to "value"))
        assertNotNull(result)
        assertEquals("my-skill", calledWithName)
        assertEquals("value", calledWithParams["key"])
    }

    @Test
    fun `python executor callback works`() = runBlocking {
        var called = false
        registry.pythonExecutor = { script, _ ->
            called = true
            ToolResult(success = true, output = script)
        }

        val result = registry.pythonExecutor?.invoke("print('hello')", emptyMap())
        assertNotNull(result)
        assertTrue(called)
        assertEquals("print('hello')", result?.output)
    }
}
