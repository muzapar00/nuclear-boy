package com.nuclearboy.common

/**
 * Domain-level error types with user-friendly messages.
 * Each error has a humanMessage for display and a code for logging.
 */
enum class AppError(
    val humanMessage: String,
    val code: String
) {
    // Network
    NetworkTimeout("网络好像有点卡…我再试一次？😅", "ERR_NET_TIMEOUT"),
    NetworkUnavailable("看起来没有网络连接 📡", "ERR_NET_OFFLINE"),
    ServerError("DeepSeek 那边好像有点忙，稍等一下…", "ERR_SERVER"),

    // API
    ApiKeyInvalid("这个 API Key 好像不对，要不要检查一下？🔑", "ERR_API_KEY"),
    InsufficientBalance("哎呀，DeepSeek 余额不太够了。要不要我帮你看看怎么充值？🔋", "ERR_BALANCE"),
    RateLimited("我们太快了！DeepSeek 让我们慢一点…等一下就好 ⏳", "ERR_RATE_LIMIT"),

    // File System
    FileNotFound("找不到这个文件了 🤷", "ERR_FILE_NOT_FOUND"),
    FileWriteDenied("文件保存失败了，可能是权限问题 🔒", "ERR_FILE_WRITE"),
    FileReadError("文件读取出错了 📄", "ERR_FILE_READ"),
    StorageFull("存储空间不够了（剩余 %.0f MB），清理一下？🗑️", "ERR_STORAGE_FULL"),

    // Python Sandbox
    PythonRuntimeError("Python 运行出错了 🔧", "ERR_PYTHON_RT"),
    PythonTimeout("Python 脚本跑了太久，我把它停了 ⏰", "ERR_PYTHON_TIMEOUT"),
    PythonPackageError("安装 Python 包失败了 📦", "ERR_PYTHON_PKG"),
    SandboxPermissionDenied("这个操作不被沙箱允许 🛡️", "ERR_SANDBOX_PERM"),

    // Skills
    SkillNotFound("找不到这个 Skill 🧩", "ERR_SKILL_NOT_FOUND"),
    SkillInstallFailed("Skill 安装失败了", "ERR_SKILL_INSTALL"),
    SkillPermissionDenied("这个 Skill 需要的权限被拒绝了", "ERR_SKILL_PERM"),

    // Memory
    MemoryWriteFailed("记忆保存失败 🧠", "ERR_MEM_WRITE"),
    MemoryReadFailed("记忆读取失败", "ERR_MEM_READ"),

    // General
    InvalidRequest("请求格式有误，内部数据可能不一致，请重启对话 📋", "ERR_INVALID_REQ"),
    Unknown("哎呀，好像出了点问题，我帮你重试一下？", "ERR_UNKNOWN"),
    UserCancelled("已取消", "ERR_CANCELLED");

    val isRetryable: Boolean
        get() = this in setOf(
            NetworkTimeout, NetworkUnavailable, ServerError,
            RateLimited, Unknown, PythonTimeout
        )

    val shouldShowBalancePrompt: Boolean
        get() = this == InsufficientBalance

    val shouldShowApiKeyPrompt: Boolean
        get() = this == ApiKeyInvalid

    companion object {
        fun fromHttpCode(code: Int): AppError = when (code) {
            400 -> InvalidRequest
            401 -> ApiKeyInvalid
            402 -> InsufficientBalance
            429 -> RateLimited
            500, 502, 503, 504 -> ServerError
            else -> Unknown
        }
    }
}
