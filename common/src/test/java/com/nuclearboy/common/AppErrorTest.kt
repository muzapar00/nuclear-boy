package com.nuclearboy.common

import org.junit.Assert.*
import org.junit.Test

class AppErrorTest {

    @Test
    fun `401 maps to ApiKeyInvalid`() {
        assertEquals(AppError.ApiKeyInvalid, AppError.fromHttpCode(401))
    }

    @Test
    fun `402 maps to InsufficientBalance`() {
        assertEquals(AppError.InsufficientBalance, AppError.fromHttpCode(402))
    }

    @Test
    fun `429 maps to RateLimited`() {
        assertEquals(AppError.RateLimited, AppError.fromHttpCode(429))
    }

    @Test
    fun `5xx maps to ServerError`() {
        assertEquals(AppError.ServerError, AppError.fromHttpCode(500))
        assertEquals(AppError.ServerError, AppError.fromHttpCode(502))
        assertEquals(AppError.ServerError, AppError.fromHttpCode(503))
        assertEquals(AppError.ServerError, AppError.fromHttpCode(504))
    }

    @Test
    fun `unknown code maps to Unknown`() {
        assertEquals(AppError.Unknown, AppError.fromHttpCode(418))
        assertEquals(AppError.Unknown, AppError.fromHttpCode(200))
    }

    @Test
    fun `network errors are retryable`() {
        assertTrue(AppError.NetworkTimeout.isRetryable)
        assertTrue(AppError.NetworkUnavailable.isRetryable)
        assertTrue(AppError.ServerError.isRetryable)
        assertTrue(AppError.RateLimited.isRetryable)
    }

    @Test
    fun `permanent errors are not retryable`() {
        assertFalse(AppError.ApiKeyInvalid.isRetryable)
        assertFalse(AppError.InsufficientBalance.isRetryable)
        assertFalse(AppError.FileNotFound.isRetryable)
    }

    @Test
    fun `balance error triggers prompt`() {
        assertTrue(AppError.InsufficientBalance.shouldShowBalancePrompt)
        assertFalse(AppError.NetworkTimeout.shouldShowBalancePrompt)
    }

    @Test
    fun `api key error triggers prompt`() {
        assertTrue(AppError.ApiKeyInvalid.shouldShowApiKeyPrompt)
        assertFalse(AppError.ServerError.shouldShowApiKeyPrompt)
    }

    @Test
    fun `all errors have human messages`() {
        AppError.entries.forEach { error ->
            assertTrue(
                "Error ${error.name} has no human message",
                error.humanMessage.isNotBlank()
            )
            assertTrue(
                "Error ${error.name} has no error code",
                error.code.isNotBlank()
            )
        }
    }

    @Test
    fun `error codes are unique`() {
        val codes = AppError.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}
