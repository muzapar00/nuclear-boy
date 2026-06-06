package com.nuclearboy.common

import org.junit.Assert.*
import org.junit.Test

class AppResultTest {

    @Test
    fun `success holds data`() {
        val result = AppResult.success(42)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `failure holds error with message`() {
        val result = AppResult.failure(AppError.NetworkTimeout)
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
        assertEquals("网络好像有点卡…我再试一次？😅", (result as AppResult.Failure).error.humanMessage)
    }

    @Test
    fun `map transforms success data`() {
        val result = AppResult.success(10).map { it * 2 }
        assertEquals(20, result.getOrNull())
    }

    @Test
    fun `map passes through failure`() {
        val result: AppResult<Int> = AppResult.failure(AppError.Unknown)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isFailure)
    }

    @Test
    fun `onSuccess executes action`() {
        var called = false
        AppResult.success("hello").onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onFailure executes action`() {
        var error: AppError? = null
        AppResult.failure(AppError.ApiKeyInvalid).onFailure { error = it }
        assertEquals(AppError.ApiKeyInvalid, error)
    }

    @Test
    fun `runCatching catches exceptions`() {
        val result = AppResult.runCatching { "hello".substring(100) }
        assertTrue(result.isFailure)
        assertEquals(AppError.Unknown, (result as AppResult.Failure).error)
    }

    @Test
    fun `runCatching returns success for valid code`() {
        val result = AppResult.runCatching { 1 + 1 }
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
    }
}
