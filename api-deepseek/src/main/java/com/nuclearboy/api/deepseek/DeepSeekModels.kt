package com.nuclearboy.api.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek API request/response DTOs. OpenAI-compatible format.
 */

// ── Request ──────────────────────────────────────────

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double = 1.0,
    @SerialName("top_p") val topP: Double = 1.0,
    @SerialName("max_tokens") val maxTokens: Int = 8192,
    val stream: Boolean = true,
    val tools: List<ToolDefinitionDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val thinking: ThinkingConfigDto? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String?,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ThinkingConfigDto(
    val type: String,  // "enabled" or "disabled"
)

@Serializable
data class ToolDefinitionDto(
    val type: String = "function",
    val function: FunctionDefinitionDto,
)

@Serializable
data class FunctionDefinitionDto(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject? = null,
)

// ── Response ─────────────────────────────────────────

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val choices: List<ChoiceDto>,
    val usage: UsageDto? = null,
    val created: Long? = null,
)

@Serializable
data class ChoiceDto(
    val index: Int,
    val message: MessageDto? = null,
    val delta: DeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDeltaDto>? = null,
)

@Serializable
data class ToolCallDeltaDto(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCallDeltaDto? = null,
)

@Serializable
data class FunctionCallDeltaDto(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDto,
)

@Serializable
data class FunctionCallDto(
    val name: String,
    val arguments: String,
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetailsDto? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetailsDto? = null,
)

@Serializable
data class CompletionTokensDetailsDto(
    @SerialName("reasoning_tokens") val reasoningTokens: Long? = 0,
)

@Serializable
data class PromptTokensDetailsDto(
    @SerialName("cached_tokens") val cachedTokens: Long? = 0,
)

// ── Streaming ─────────────────────────────────────────

@Serializable
data class StreamChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
)

// ── Balance Check ─────────────────────────────────────

@Serializable
data class BalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfo> = emptyList(),
)

@Serializable
data class BalanceInfo(
    val currency: String = "CNY",
    @SerialName("total_balance") val totalBalance: String = "0.00",
    @SerialName("granted_balance") val grantedBalance: String = "0.00",
    @SerialName("topped_up_balance") val toppedUpBalance: String = "0.00",
)

// ── Error ─────────────────────────────────────────────

@Serializable
data class DeepSeekErrorResponse(
    val error: DeepSeekErrorDetail? = null,
)

@Serializable
data class DeepSeekErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null,
)
