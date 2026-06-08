package com.nuclearboy.skills

import kotlinx.serialization.Serializable

/**
 * Complete manifest describing a skill's metadata, permissions, parameters,
 * and entry point. Serialized to/from skill.yaml files in each skill directory.
 */
@Serializable
data class SkillManifest(
    val name: String,
    val version: String,
    val description: String,
    val author: String = "community",
    val homepage: String? = null,
    val permissions: SkillPermissions,
    val parameters: List<SkillParameter> = emptyList(),
    val entryPoint: String = "main:run",
    val triggers: SkillTriggers? = null,
)

/**
 * Automatic trigger conditions for a skill.
 */
@Serializable
data class SkillTriggers(
    val on_startup: Boolean = false,
    val on_new_project: Boolean = false,
)

/**
 * Permission model for safely scoping what a skill can access.
 * Each sub-permission acts as an allowlist; null means "not requested".
 */
@Serializable
data class SkillPermissions(
    val filesystem: FilesystemPermissions? = null,
    val network: NetworkPermission? = null,
    val packages: PackagePermission? = null,
    val shell: ShellPermission? = null,
) {

    /**
     * Whether this skill requests any permission at all.
     */
    val requestsAnyPermission: Boolean
        get() = filesystem != null || network != null || packages != null || shell != null

    /**
     * Checks if the skill is effectively sandboxed (no network, no shell,
     * filesystem restricted to workspace).
     */
    val isSandboxed: Boolean
        get() {
            if (network?.allowed == true) return false
            if (shell?.allowed == true) return false
            if (filesystem != null) {
                val read = filesystem!!.read
                val write = filesystem!!.write
                val hasExternalRead = read.any { !it.startsWith("workspace") }
                val hasExternalWrite = write.any { !it.startsWith("workspace") }
                if (hasExternalRead || hasExternalWrite) return false
            }
            return true
        }
}

/**
 * Filesystem permission scopes. Each entry is a glob-style path pattern.
 * The default allows only the sandbox workspace.
 */
@Serializable
data class FilesystemPermissions(
    val read: List<String> = listOf("workspace/**"),
    val write: List<String> = listOf("workspace/**"),
) {
    /**
     * Whether a given path is covered by the read allowlist.
     * Simple prefix/glob check — full glob matching can be added later.
     */
    fun canRead(path: String): Boolean = read.any { matchesGlob(path, it) }

    /**
     * Whether a given path is covered by the write allowlist.
     */
    fun canWrite(path: String): Boolean = write.any { matchesGlob(path, it) }

    companion object {
        /**
         * Minimal glob matching supporting `**` and single `*`.
         */
        fun matchesGlob(path: String, pattern: String): Boolean {
            val normalizedPath = path.trimStart('/').replace('\\', '/')
            val normalizedPattern = pattern.trimStart('/').replace('\\', '/')

            // Full ** match
            if (normalizedPattern == "**") return true
            if (normalizedPattern == "workspace/**") {
                return normalizedPath.startsWith("workspace/") || normalizedPath == "workspace"
            }

            // Convert glob to regex
            val regex = buildRegex(normalizedPattern)
            return Regex(regex).matches(normalizedPath)
        }

        private fun buildRegex(pattern: String): String {
            val sb = StringBuilder()
            sb.append('^')
            var i = 0
            while (i < pattern.length) {
                when {
                    pattern.startsWith("**", i) -> {
                        sb.append(".*")
                        i += 2
                    }
                    pattern[i] == '*' -> {
                        sb.append("[^/]*")
                        i++
                    }
                    pattern[i] in ".+?^$\\()[]{}|" -> {
                        sb.append('\\').append(pattern[i])
                        i++
                    }
                    else -> {
                        sb.append(pattern[i])
                        i++
                    }
                }
            }
            sb.append('$')
            return sb.toString()
        }
    }
}

/**
 * Whether the skill is allowed to make outbound network requests.
 */
@Serializable
data class NetworkPermission(
    val allowed: Boolean = false,
    val allowedHosts: List<String> = emptyList(),
) {
    fun isHostAllowed(host: String): Boolean {
        if (allowed && allowedHosts.isEmpty()) return true
        return allowedHosts.any { h ->
            host == h || (h.startsWith("*.") && host.endsWith(h.removePrefix("*")))
        }
    }
}

/**
 * Python packages the skill is permitted to install (at install time).
 * An empty list means none allowed beyond what is bundled.
 */
@Serializable
data class PackagePermission(
    val allowed: List<String> = emptyList(),
)

/**
 * Whether the skill may execute arbitrary shell commands.
 * Should almost always be false.
 */
@Serializable
data class ShellPermission(
    val allowed: Boolean = false,
)

/**
 * A single parameter accepted by the skill.
 */
@Serializable
data class SkillParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val default: String? = null,
) {
    /**
     * Validates a parameter value against the expected type.
     */
    fun validate(value: String?): Boolean {
        if (value == null) return !required
        return when (type.lowercase()) {
            "string" -> true
            "int", "integer" -> value.toIntOrNull() != null
            "float", "double", "number" -> value.toDoubleOrNull() != null
            "bool", "boolean" -> value.lowercase() in setOf("true", "false", "1", "0")
            "choice" -> true // validated against default choices elsewhere
            else -> true
        }
    }
}
