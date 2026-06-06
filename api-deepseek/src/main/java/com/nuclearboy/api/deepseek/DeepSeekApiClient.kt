package com.nuclearboy.api.deepseek

import com.nuclearboy.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.BufferedReader
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

/**
 * Main DeepSeek API client.
 *
 * Handles:
 * - Chat completions (streaming + non-streaming)
 * - API key validation and balance checking
 * - Automatic retry with exponential backoff
 * - Rate limit handling
 * - Error classification with user-friendly messages
 * - SSL error handling (China network friendly)
 */
class DeepSeekApiClient(
    private val apiKeyProvider: () -> String?,
    private val tokenTracker: TokenTracker = TokenTracker(),
    val contextManager: ContextWindowManager = ContextWindowManager(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true  // Required for ToolDefinitionDto.type="function"
    }

    private val random = SecureRandom()

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            @Suppress("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) {}
                override fun checkServerTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )

        val sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.3")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .connectTimeout(AppConstants.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConstants.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConstants.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val apiKey = apiKeyProvider() ?: run {
                    throw IOException("API Key not configured")
                }
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    // ── Public API ─────────────────────────────────────

    /**
     * Send a streaming chat completion request.
     * Emits [StreamEvent] for each chunk received.
     */
    fun streamChat(
        messages: List<MessageDto>,
        modelTier: ModelTier = ModelTier.V4_PRO,
        thinkingMode: ThinkingMode = ThinkingMode.DISABLED,
        tools: List<ToolDefinitionDto>? = null,
    ): Flow<StreamEvent> = flow {
        val model = modelTier.modelId
        val request = buildRequest(messages, model, thinkingMode, tools, stream = true)
        android.util.Log.e("NuclearBoy", "[ApiClient] streamChat() ENTRY model=$model msgs=${messages.size} tools=${tools?.size ?: 0} thinking=$thinkingMode stream=true")
        val clientStartMs = System.currentTimeMillis()

        val promptTokens = estimatePromptTokens(messages)
        tokenTracker.startRequest(modelTier, thinkingMode.apiValue, promptTokens)

        emit(StreamEvent.Thinking("正在思考…"))

        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount <= AppConstants.MAX_RETRIES) {
            try {
                val httpRequest = buildHttpRequest(request)
                android.util.Log.e("NuclearBoy", "[ApiClient] streamChat() HTTP request sent url=${httpRequest.url} bodySize=${httpRequest.body?.contentLength() ?: -1}")
                val response = client.newCall(httpRequest).execute()

                android.util.Log.e("NuclearBoy", "[ApiClient] HTTP ${response.code} contentLen=${response.body?.contentLength()}")
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    android.util.Log.e("NuclearBoy", "API: ERROR body=$body")
                    val errorResponse = try {
                        json.decodeFromString<DeepSeekErrorResponse>(body)
                    } catch (_: Exception) { null }
                    throw DeepSeekHttpException(
                        code = response.code,
                        message = errorResponse?.error?.message ?: "HTTP ${response.code}",
                        errorType = errorResponse?.error?.type,
                    )
                }

                val body = response.body ?: throw IOException("Empty response body")
                android.util.Log.e("NuclearBoy", "[ApiClient] SSE streaming start")
                val reader = body.charStream().buffered()
                var content = StringBuilder()
                var reasoningContent = StringBuilder()
                var finalUsage: UsageDto? = null
                var toolCallsDetected = false
                var lineCount = 0

                reader.useLines { lines ->
                    for (line in lines) {
                        if (line.isEmpty()) continue
                        if (line == "data: [DONE]") break
                        if (!line.startsWith("data: ")) continue
                        lineCount++
                        if (lineCount % 50 == 0) {
                            android.util.Log.e("NuclearBoy", "[ApiClient] SSE line $lineCount processed, content=${content.length} reasoning=${reasoningContent.length}")
                        }
                        val jsonStr = line.removePrefix("data: ")
                        if (jsonStr.isBlank()) continue

                        try {
                            val chunk = json.decodeFromString<StreamChunk>(jsonStr)
                            chunk.usage?.let { finalUsage = it }

                            chunk.choices.forEach { choice ->
                                val delta = choice.delta ?: return@forEach

                                if (!delta.reasoningContent.isNullOrEmpty()) {
                                    reasoningContent.append(delta.reasoningContent)
                                    emit(StreamEvent.Thinking(delta.reasoningContent))
                                    tokenTracker.onStreamToken(isReasoning = true)
                                }
                                if (!delta.content.isNullOrEmpty()) {
                                    content.append(delta.content)
                                    emit(StreamEvent.Content(delta.content, isReasoning = false))
                                    tokenTracker.onStreamToken(isReasoning = false)
                                }
                                delta.toolCalls?.forEach { toolDelta ->
                                    toolCallsDetected = true
                                    val id = toolDelta.id
                                    if (id != null) {
                                        emit(StreamEvent.ToolCallRequest(
                                            listOf(ToolCallDto(id = id, function = FunctionCallDto(
                                                name = toolDelta.function?.name ?: "",
                                                arguments = toolDelta.function?.arguments ?: "")))))
                                    } else {
                                        val fnArgs = toolDelta.function?.arguments
                                        if (fnArgs != null) {
                                            emit(StreamEvent.ToolCallDelta(listOf(ToolCallDeltaDto(
                                                index = toolDelta.index, function = FunctionCallDeltaDto(arguments = fnArgs)
                                            ))))
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) { continue }
                    }
                }

                val usage = finalUsage ?: UsageDto(
                    promptTokens = promptTokens,
                    completionTokens = content.length.toLong(),
                    totalTokens = promptTokens + content.length,
                )
                val clientElapsedMs = System.currentTimeMillis() - clientStartMs
                android.util.Log.e("NuclearBoy", "[ApiClient] SSE stream complete lines=$lineCount contentLen=${content.length} reasoningLen=${reasoningContent.length} toolCalls=$toolCallsDetected usage=prompt=${usage.promptTokens} completion=${usage.completionTokens} total=${usage.totalTokens} cached=${usage.promptTokensDetails?.cachedTokens ?: 0} reasoningTokens=${usage.completionTokensDetails?.reasoningTokens ?: 0} elapsedMs=$clientElapsedMs")
                tokenTracker.onRequestComplete(usage)
                emit(StreamEvent.Complete(usage))
                return@flow

            } catch (e: Exception) {
                lastException = e
                val appError = classifyError(e)
                android.util.Log.e("NuclearBoy", "[ApiClient] streamChat() error retryCount=$retryCount maxRetries=${AppConstants.MAX_RETRIES} appError=$appError isRetryable=${appError.isRetryable}")
                if (!appError.isRetryable || retryCount >= AppConstants.MAX_RETRIES) {
                    android.util.Log.e("NuclearBoy", "[ApiClient] streamChat() non-retryable or max retries reached, emitting error")
                    emit(StreamEvent.Error(appError, e.message))
                    return@flow
                }
                retryCount++
                val delayMs = AppConstants.RETRY_BASE_DELAY_MS * (1 shl (retryCount - 1)) + (random.nextLong() % 500)
                android.util.Log.e("NuclearBoy", "[ApiClient] streamChat() retrying attempt=$retryCount delayMs=$delayMs")
                emit(StreamEvent.Thinking("重试第 ${retryCount} 次…"))
                delay(delayMs)
            }
        }
        lastException?.let { emit(StreamEvent.Error(classifyError(it), it.message)) }
    }

    /**
     * Validate an API key by making a lightweight request.
     */
    suspend fun validateApiKey(apiKey: String): AppResult<Boolean> = withContext(Dispatchers.IO) {
        android.util.Log.e("NuclearBoy", "[ApiClient] validateApiKey() ENTRY")
        try {
            val testMessages = listOf(
                MessageDto(role = "user", content = "Hi")
            )
            val request = buildRequest(
                messages = testMessages,
                model = ModelTier.V4_FLASH.modelId,
                thinkingMode = ThinkingMode.DISABLED,
                tools = null,
                stream = false,
                maxTokens = 1,
            )

            val httpRequest = buildHttpRequest(request)
            val response = client.newCall(httpRequest).execute()

            val result = when (response.code) {
                200 -> AppResult.success(true)
                401 -> AppResult.failure(AppError.ApiKeyInvalid)
                402 -> AppResult.failure(AppError.InsufficientBalance)
                429 -> AppResult.failure(AppError.RateLimited)
                in 500..599 -> AppResult.failure(AppError.ServerError)
                else -> AppResult.failure(AppError.Unknown, "HTTP ${response.code}")
            }
            android.util.Log.e("NuclearBoy", "[ApiClient] validateApiKey() response httpCode=${response.code} result=${result}")
            result
        } catch (e: SSLException) {
            android.util.Log.e("NuclearBoy", "[ApiClient] validateApiKey() SSLException: ${e.message}")
            AppResult.failure(
                AppError.NetworkUnavailable,
                "SSL 连接失败，网络可能被干扰。请尝试切换网络环境。"
            )
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("NuclearBoy", "[ApiClient] validateApiKey() SocketTimeoutException: ${e.message}")
            AppResult.failure(AppError.NetworkTimeout)
        } catch (e: IOException) {
            android.util.Log.e("NuclearBoy", "[ApiClient] validateApiKey() IOException: ${e.message}")
            AppResult.failure(AppError.NetworkUnavailable, e.message)
        }
    }

    /**
     * Check account balance via the DeepSeek API.
     */
    suspend fun checkBalance(apiKey: String): AppResult<BalanceResponse> =
        withContext(Dispatchers.IO) {
            android.util.Log.e("NuclearBoy", "[ApiClient] checkBalance() ENTRY")
            try {
                val httpRequest = Request.Builder()
                    .url("${AppConstants.DEEPSEEK_BASE_URL}/user/balance")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(httpRequest).execute()
                val body = response.body?.string() ?: ""
                android.util.Log.e("NuclearBoy", "[ApiClient] checkBalance() httpCode=${response.code} bodyLen=${body.length}")

                if (response.isSuccessful) {
                    val balance = json.decodeFromString<BalanceResponse>(body)
                    android.util.Log.e("NuclearBoy", "[ApiClient] checkBalance() success isAvailable=${balance.isAvailable} totalBalance=${balance.balanceInfos?.joinToString { "${it.currency}:${it.totalBalance}" }}")
                    AppResult.success(balance)
                } else {
                    val errorBody = try {
                        json.decodeFromString<DeepSeekErrorResponse>(body)
                    } catch (_: Exception) { null }
                    AppResult.failure(
                        error = AppError.fromHttpCode(response.code),
                        detail = errorBody?.error?.message ?: body
                    )
                }
            } catch (e: Exception) {
                AppResult.failure(
                    error = classifyError(e),
                    detail = e.message
                )
            }
        }

    // ── Private ────────────────────────────────────────

    private fun buildRequest(
        messages: List<MessageDto>,
        model: String,
        thinkingMode: ThinkingMode,
        tools: List<ToolDefinitionDto>?,
        stream: Boolean,
        maxTokens: Int = 8192,
    ): ChatCompletionRequest {
        android.util.Log.e("NuclearBoy", "[ApiClient] buildRequest() model=$model stream=$stream thinking=$thinkingMode tools=${tools?.size ?: 0} messages=${messages.size} maxTokens=$maxTokens")
        return ChatCompletionRequest(
            model = model,
            messages = sanitizeMessages(messages),
            temperature = 1.0,
            topP = 1.0,
            maxTokens = maxTokens,
            stream = stream,
            tools = tools,
            thinking = if (thinkingMode != ThinkingMode.DISABLED) {
                ThinkingConfigDto(type = "enabled")
            } else {
                ThinkingConfigDto(type = "disabled")  // Must be explicit: DeepSeek defaults to enabled!
            },
            reasoningEffort = when (thinkingMode) {
                ThinkingMode.HIGH -> "high"
                ThinkingMode.MAX -> "max"
                ThinkingMode.DISABLED -> null
            },
        )
    }

    /**
     * DeepSeek API now REQUIRES reasoning_content to be passed back
     * in thinking mode (policy changed ~2026).
     * We keep it intact.
     */
    private fun sanitizeMessages(messages: List<MessageDto>): List<MessageDto> {
        // No-op: reasoning_content must be preserved for thinking mode
        return messages
    }

    private fun buildHttpRequest(request: ChatCompletionRequest): okhttp3.Request {
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
        android.util.Log.e("NuclearBoy", "[ApiClient] buildHttpRequest() bodySize=${body.length} bytes")
        val requestBody = body.toRequestBody("application/json".toMediaType())

        return okhttp3.Request.Builder()
            .url("${AppConstants.DEEPSEEK_BASE_URL}/v1/chat/completions")
            .post(requestBody)
            .build()
    }

    private fun classifyError(e: Exception): AppError {
        val result = when (e) {
            is DeepSeekHttpException -> AppError.fromHttpCode(e.code)
            is SSLException -> AppError.NetworkUnavailable
            is java.net.SocketTimeoutException -> AppError.NetworkTimeout
            is IOException -> {
                val msg = e.message?.lowercase() ?: ""
                when {
                    "timeout" in msg -> AppError.NetworkTimeout
                    "connect" in msg || "resolve" in msg -> AppError.NetworkUnavailable
                    "ssl" in msg -> AppError.NetworkUnavailable
                    else -> AppError.Unknown
                }
            }
            else -> AppError.Unknown
        }
        android.util.Log.e("NuclearBoy", "[ApiClient] classifyError() exceptionType=${e.javaClass.simpleName} message=${e.message} result=$result")
        return result
    }

    private fun estimatePromptTokens(messages: List<MessageDto>): Long {
        val result = messages.sumOf { msg ->
            ((msg.content?.length ?: 0) + (msg.reasoningContent?.length ?: 0)) / 3L
        }
        android.util.Log.e("NuclearBoy", "[ApiClient] estimatePromptTokens() totalChars=${
            messages.sumOf { (it.content?.length ?: 0) + (it.reasoningContent?.length ?: 0) }
        } estimatedTokens=$result messages=${messages.size}")
        return result
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

// ── Supporting Types ──────────────────────────────────

sealed class StreamEvent {
    data class Thinking(val message: String) : StreamEvent()
    data class Content(val text: String, val isReasoning: Boolean = false) : StreamEvent()
    data class ToolCallRequest(val toolCalls: List<ToolCallDto>) : StreamEvent()
    data class ToolCallDelta(val deltas: List<ToolCallDeltaDto>) : StreamEvent()
    data class Complete(val usage: UsageDto) : StreamEvent()
    data class Error(val appError: AppError, val technicalDetail: String?) : StreamEvent()
}

class DeepSeekHttpException(
    val code: Int,
    message: String,
    val errorType: String?,
) : IOException(message)
