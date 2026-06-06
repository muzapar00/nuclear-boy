package com.nuclearboy.common

import java.time.LocalTime

/**
 * Extension functions used across the app.
 */

/** Check if current time is in night mode range */
fun LocalTime.isNightMode(): Boolean {
    return hour >= AppConstants.NIGHT_MODE_START_HOUR ||
            hour < AppConstants.NIGHT_MODE_END_HOUR
}

/** Get a human-friendly time-of-day greeting */
fun LocalTime.toGreeting(): String = when (hour) {
    in 0..5 -> "夜深了"
    in 6..8 -> "早上好"
    in 9..11 -> "上午好"
    in 12..13 -> "中午好"
    in 14..17 -> "下午好"
    in 18..21 -> "晚上好"
    else -> "夜深了"
}

/** Truncate string to max length, append ellipsis if truncated */
fun String.truncate(maxLength: Int, ellipsis: String = "…"): String {
    return if (length > maxLength) take(maxLength) + ellipsis else this
}

/** Mask API key for display: sk-v4-****a1b2 */
fun String.maskApiKey(): String {
    if (length <= 12) return "****"
    return "${take(8)}****${takeLast(4)}"
}

/** Convert file size to human-readable format */
fun Long.toFileSizeString(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        this < 1024 * 1024 * 1024 -> "${"%.1f".format(this / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(this / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

/** Get file extension from filename */
fun String.fileExtension(): String {
    val lastDot = lastIndexOf('.')
    return if (lastDot >= 0) substring(lastDot + 1).lowercase() else ""
}

/** Check if this is a text file based on extension */
fun String.isTextFile(): Boolean {
    val textExtensions = setOf(
        "txt", "md", "kt", "java", "py", "js", "ts", "tsx", "jsx",
        "html", "css", "scss", "json", "xml", "yaml", "yml", "toml",
        "gradle", "properties", "sh", "bash", "zsh", "fish",
        "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "swift",
        "sql", "graphql", "proto", "cfg", "ini", "conf",
        "gitignore", "dockerignore", "editorconfig",
    )
    return fileExtension() in textExtensions
}

/** Check if a file is a Word/Excel/PDF document */
fun String.isDocumentFile(): Boolean {
    val docExtensions = setOf("docx", "xlsx", "pptx", "pdf", "doc", "xls", "ppt")
    return fileExtension() in docExtensions
}

/**
 * Build a display-friendly relative time string.
 * "刚刚", "3分钟前", "2小时前", "昨天", etc.
 */
fun Long.toRelativeTimeString(now: Long = System.currentTimeMillis()): String {
    val diff = now - this
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 172_800_000 -> "昨天"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> "${diff / 604_800_000}周前"
    }
}

/** Append emoji based on mood/time for friendly messages */
fun String.withMoodEmoji(): String {
    // Simple heuristic: don't add if already has emoji
    if (any { it.code > 0x1F300 }) return this
    return when {
        contains("错") || contains("失败") -> "$this 😅"
        contains("成功") || contains("好了") || contains("搞定") -> "$this ✨"
        contains("试") || contains("稍等") -> "$this ⏳"
        contains("注意") || contains("小心") -> "$this ⚠️"
        else -> this
    }
}
