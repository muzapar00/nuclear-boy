package com.nuclearboy.common

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Core domain models used across all modules.
 */

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rootPath: String,
    val description: String = "",
    val techStack: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val fileCount: Int = 0,
    val isArchived: Boolean = false,
)

@Serializable
data class FileInfo(
    val path: String,
    val name: String,
    val extension: String = "",
    val size: Long = 0,
    val lastModified: Long = 0,
    val isDirectory: Boolean = false,
    val content: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val attachedFiles: List<FileInfo> = emptyList(),
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val fileChanges: List<FileChange> = emptyList(),
    val tokenUsage: TokenUsage? = null,
)

@Serializable
enum class MessageRole(val displayName: String) {
    USER("你"),
    ASSISTANT("核弹男孩"),
    SYSTEM("系统"),
    TOOL("工具");
}

@Serializable
enum class MessageStatus(val description: String) {
    SENDING("发送中…"),
    SENT(""),
    THINKING("思考中…"),
    STREAMING("输出中…"),
    EXECUTING("正在操作…"),
    COMPLETE(""),
    ERROR("出了点问题"),
    CANCELLED("已取消");
}

@Serializable
data class ToolCallRecord(
    val toolName: String,
    val input: String,
    val output: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val toolCallId: String? = null,
)

@Serializable
enum class ToolCallStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

@Serializable
data class TokenUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val cachedPromptTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val estimatedCostUsd: Double = 0.0,
)

@Serializable
data class SessionStats(
    val sessionId: String,
    val startedAt: Long,
    val requestCount: Int = 0,
    val totalPromptTokens: Long = 0,
    val totalCompletionTokens: Long = 0,
    val totalCachedTokens: Long = 0,
    val totalReasoningTokens: Long = 0,
    val totalCostUsd: Double = 0.0,
    val averageSpeed: Double = 0.0,
    val averageLatencyMs: Long = 0,
)

@Serializable
data class UserProfile(
    val preferredLanguages: List<String> = emptyList(),
    val preferredFrameworks: List<String> = emptyList(),
    val codeStyle: CodeStyle = CodeStyle(),
    val interactionStyle: InteractionStyle = InteractionStyle(),
    val workSchedule: WorkSchedule = WorkSchedule(),
    val monthlyBudget: Double = 50.0,
    val totalApiCostThisMonth: Double = 0.0,
)

@Serializable
data class CodeStyle(
    val indentSize: Int = 2,
    val useTabs: Boolean = false,
    val preferSingleQuotes: Boolean = true,
    val preferSemicolons: Boolean = false,
    val preferFunctional: Boolean = true,
    val commentLanguage: String = "zh",
)

@Serializable
data class InteractionStyle(
    val verbosity: Verbosity = Verbosity.Detailed,
    val confirmBeforeAction: Boolean = true,
    val autoSwitchModel: Boolean = true,
    val preferredModel: ModelTier = ModelTier.V4_PRO,
)

@Serializable
enum class Verbosity(val label: String) {
    Brief("直接给代码"),
    Normal("适中的解释"),
    Detailed("详细的解释"),
}

@Serializable
data class WorkSchedule(
    val weekdayWorkModel: ModelTier = ModelTier.V4_PRO,
    val eveningModel: ModelTier = ModelTier.V4_FLASH,
    val weekendModel: ModelTier = ModelTier.V4_FLASH,
    val nightModel: ModelTier = ModelTier.V4_FLASH,
)

@Serializable
enum class ModelTier(val modelId: String, val displayName: String, val description: String) {
    V4_PRO("deepseek-v4-pro", "V4 Pro", "最强能力 · 复杂任务首选"),
    V4_FLASH("deepseek-v4-flash", "V4 Flash", "快速且省钱 · 日常使用"),
}

@Serializable
enum class ThinkingMode(val apiValue: String, val displayName: String) {
    DISABLED("disabled", "快速模式"),
    HIGH("high", "深度思考"),
    MAX("max", "极致推理"),
}

@Serializable
data class SkillInfo(
    val name: String,
    val description: String,
    val isProjectSkill: Boolean = false,
)

@Serializable
data class FileChange(
    val filePath: String,
    val changeType: ChangeType,
    val diff: String? = null,
)

@Serializable
enum class ChangeType(val label: String) {
    CREATED("创建"),
    MODIFIED("修改"),
    DELETED("删除"),
}
