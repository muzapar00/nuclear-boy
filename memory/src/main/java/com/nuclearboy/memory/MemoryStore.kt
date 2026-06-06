package com.nuclearboy.memory

import android.content.Context
import com.nuclearboy.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// ── Memory Search Result ────────────────────────────────

/**
 * Result of a memory search, combining results from all three layers.
 */
data class MemorySearchResult(
    val projectMemories: List<ProjectMemoryEntity> = emptyList(),
    val profileEntries: List<UserProfileEntity> = emptyList(),
    val semanticMemories: List<SemanticMemoryEntity> = emptyList(),
) {
    val totalResults: Int
        get() = projectMemories.size + profileEntries.size + semanticMemories.size
    /**
     * Build a compact, token-efficient string for injection into the system prompt.
     */
    fun toCompactString(maxTokens: Long = AppConstants.BUDGET_USER_PROFILE): String {
        val sb = StringBuilder()

        // High-confidence profile entries first
        val confidentProfiles = profileEntries.filter { it.confidence >= 0.6f }
        if (confidentProfiles.isNotEmpty()) {
            sb.appendLine("[用户偏好]")
            confidentProfiles.take(8).forEach { entry ->
                sb.appendLine("- ${entry.key}: ${entry.value}")
            }
        }

        // Key project memories
        if (projectMemories.isNotEmpty()) {
            sb.appendLine("[项目记忆]")
            projectMemories.take(5).forEach { mem ->
                sb.appendLine("- ${mem.key}: ${mem.value.take(200)}")
            }
        }

        // Top semantic memories
        if (semanticMemories.isNotEmpty()) {
            sb.appendLine("[相关知识]")
            semanticMemories.take(3).forEach { mem ->
                sb.appendLine("- ${mem.summary.take(200)}")
            }
        }

        val result = sb.toString()
        val estimatedTokens = (result.length / 3.5).toLong()
        return if (estimatedTokens > maxTokens) {
            result.take((maxTokens * 3.5).toInt()) + "\n..."
        } else {
            result
        }
    }
}

// ── Memory Store ────────────────────────────────────────

/**
 * Three-layer memory system for 核弹男孩 (NUCLEAR BOY).
 *
 * ## Architecture
 *
 * **Layer 1 — Project Memories**: What the agent knows about a specific project.
 * - Code style conventions ("this project uses 4-space indentation")
 * - Build/run commands ("to build: ./gradlew assembleDebug")
 * - Project structure ("the API module is at api-deepseek/")
 * - Project-specific preferences ("prefer Compose over XML for UI")
 *
 * **Layer 2 — User Profile**: Cross-project knowledge about the user.
 * - Preferred languages and frameworks
 * - Code style preferences
 * - Interaction preferences
 * - Work schedule patterns
 * - Each entry has a confidence score (0.0 - 1.0)
 *
 * **Layer 3 — Semantic Memories**: Abstract patterns and lessons learned.
 * - Solutions to common problems
 * - Design patterns that worked well
 * - Lessons learned from past mistakes
 * - Text-searchable with basic relevance ranking
 *
 * ## Thread Safety
 *
 * All public methods are safe to call from any coroutine context.
 * Database operations run on [Dispatchers.IO] internally.
 *
 * ## Usage
 *
 * ```kotlin
 * val memoryStore = MemoryStore(context)
 *
 * // Remember something about a project
 * memoryStore.rememberProjectDetail(
 *     projectId = "proj-123",
 *     key = "build_command",
 *     value = "./gradlew assembleDebug",
 *     category = "command"
 * )
 *
 * // Update user profile
 * memoryStore.updateUserProfile(
 *     key = "preferred_language",
 *     value = "Kotlin",
 *     category = "language",
 *     confidence = 0.9f
 * )
 *
 * // Search for relevant memories
 * val results = memoryStore.searchRelevantMemories("Android Jetpack Compose")
 *
 * // Get context string for system prompt
 * val context = memoryStore.buildContextString(projectId = "proj-123")
 * ```
 */
class MemoryStore(context: Context) {

    private val db = MemoryDatabase.getInstance(context.applicationContext)
    private val dao = db.memoryDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Layer 1: Project Memories ────────────────────────

    /**
     * Remember a detail about a project.
     *
     * If a memory with the same projectId + key already exists, it is updated.
     * Auto-increments access count for frequently-accessed keys.
     *
     * @param projectId The project this memory belongs to.
     * @param key A unique key within the project (e.g., "build_command", "code_style_indent").
     * @param value The memory value.
     * @param category Category for grouping: "code_style", "command", "structure", "preference".
     */
    suspend fun rememberProjectDetail(
        projectId: String,
        key: String,
        value: String,
        category: String = "preference",
    ): AppResult<Boolean> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val now = System.currentTimeMillis()
            val existing = dao.getProjectMemory(projectId, key)

            if (existing != null) {
                // Update existing — don't reset access count
                dao.insertProjectMemory(
                    existing.copy(
                        value = value,
                        category = category,
                        updatedAt = now,
                    )
                )
            } else {
                // New memory
                dao.insertProjectMemory(
                    ProjectMemoryEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        key = key,
                        value = value,
                        category = category,
                        createdAt = now,
                        updatedAt = now,
                        accessCount = 0,
                    )
                )
            }
            true
        }
    }

    /**
     * Record that a project memory was accessed (for ranking frequently-used info).
     */
    suspend fun touchProjectMemory(projectId: String, key: String) {
        withContext(Dispatchers.IO) {
            val memory = dao.getProjectMemory(projectId, key)
            memory?.let {
                dao.incrementAccessCount(it.id)
            }
        }
    }

    /**
     * Get a specific project memory.
     */
    suspend fun getProjectMemory(
        projectId: String,
        key: String,
    ): AppResult<ProjectMemoryEntity?> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            dao.getProjectMemory(projectId, key)
        }
    }

    /**
     * Observe a project memory as a Flow (for reactive UI).
     */
    fun observeProjectMemory(
        projectId: String,
        key: String,
    ): Flow<ProjectMemoryEntity?> = dao.observeProjectMemory(projectId, key)

    /**
     * Get all memories for a project.
     */
    suspend fun getProjectMemories(
        projectId: String,
    ): AppResult<List<ProjectMemoryEntity>> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            dao.getProjectMemories(projectId)
        }
    }

    /**
     * Get the most frequently accessed project memories.
     */
    suspend fun getFrequentProjectMemories(
        projectId: String,
        limit: Int = 20,
    ): AppResult<List<ProjectMemoryEntity>> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            dao.getFrequentlyAccessedMemories(projectId, limit)
        }
    }

    /**
     * Delete a project memory.
     */
    suspend fun forgetProjectDetail(
        projectId: String,
        key: String,
    ): AppResult<Boolean> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            dao.deleteProjectMemory(projectId, key)
            true
        }
    }

    /**
     * Clear all memories for a project (e.g., when a project is deleted).
     */
    suspend fun forgetProject(projectId: String): AppResult<Boolean> =
        withContext(Dispatchers.IO) {
            AppResult.runCatching {
                dao.deleteAllProjectMemories(projectId)
                true
            }
        }

    // ── Layer 2: User Profile ────────────────────────────

    /**
     * Update or create a user profile entry.
     *
     * Confidence management:
     * - If the key already exists with the same value, increase confidence.
     * - If the key exists with a different value, decrease confidence and update.
     * - If the key is new, start with the provided confidence.
     *
     * @param key The preference key (e.g., "preferred_language").
     * @param value The preference value.
     * @param category Category: "language", "framework", "style", "interaction", "schedule".
     * @param confidence How confident we are (0.0 = guess, 1.0 = explicit user statement).
     */
    suspend fun updateUserProfile(
        key: String,
        value: String,
        category: String = "language",
        confidence: Float = 0.7f,
    ): AppResult<Boolean> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val now = System.currentTimeMillis()
            val existing = dao.getUserProfile(key)

            if (existing != null) {
                val newConfidence = if (existing.value == value) {
                    // Reinforcing: increase confidence
                    (existing.confidence + 0.1f).coerceAtMost(1.0f)
                } else {
                    // Contradiction: update value, lower confidence
                    (existing.confidence - 0.1f).coerceAtLeast(0.3f)
                }

                dao.insertUserProfile(
                    existing.copy(
                        value = value,
                        category = category,
                        confidence = newConfidence,
                        updatedAt = now,
                    )
                )
            } else {
                dao.insertUserProfile(
                    UserProfileEntity(
                        key = key,
                        value = value,
                        category = category,
                        confidence = confidence.coerceIn(0f, 1f),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            true
        }
    }

    /**
     * Bulk import user profile from the [UserProfile] domain model.
     * Called during app startup to sync with the in-memory profile.
     */
    suspend fun importUserProfile(profile: UserProfile): AppResult<Boolean> =
        withContext(Dispatchers.IO) {
            AppResult.runCatching {
                val now = System.currentTimeMillis()

                // Languages
                profile.preferredLanguages.forEach { lang ->
                    updateUserProfile("preferred_language", lang, "language", 0.8f)
                }

                // Frameworks
                profile.preferredFrameworks.forEach { fw ->
                    updateUserProfile("preferred_framework", fw, "framework", 0.8f)
                }

                // Code style
                val cs = profile.codeStyle
                updateUserProfile("indent_size", cs.indentSize.toString(), "style", 0.9f)
                updateUserProfile("use_tabs", cs.useTabs.toString(), "style", 0.9f)
                updateUserProfile("prefer_single_quotes", cs.preferSingleQuotes.toString(), "style", 0.9f)
                updateUserProfile("prefer_semicolons", cs.preferSemicolons.toString(), "style", 0.9f)
                updateUserProfile("prefer_functional", cs.preferFunctional.toString(), "style", 0.9f)
                updateUserProfile("comment_language", cs.commentLanguage, "style", 0.9f)

                // Interaction
                val istyle = profile.interactionStyle
                updateUserProfile("verbosity", istyle.verbosity.name, "interaction", 0.9f)
                updateUserProfile("confirm_before_action", istyle.confirmBeforeAction.toString(), "interaction", 0.9f)
                updateUserProfile("auto_switch_model", istyle.autoSwitchModel.toString(), "interaction", 0.8f)
                updateUserProfile("preferred_model", istyle.preferredModel.name, "interaction", 0.8f)

                // Schedule
                val ws = profile.workSchedule
                updateUserProfile("weekday_model", ws.weekdayWorkModel.name, "schedule", 0.7f)
                updateUserProfile("evening_model", ws.eveningModel.name, "schedule", 0.7f)
                updateUserProfile("weekend_model", ws.weekendModel.name, "schedule", 0.7f)
                updateUserProfile("night_model", ws.nightModel.name, "schedule", 0.7f)

                // Budget
                updateUserProfile("monthly_budget", profile.monthlyBudget.toString(), "interaction", 0.8f)

                true
            }
        }

    /**
     * Export the stored user profile as a [UserProfile] domain model.
     */
    suspend fun exportUserProfile(): AppResult<UserProfile> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val allProfiles = dao.getHighConfidenceProfiles(0.6f)
            val map = allProfiles.associate { it.key to it.value }

            UserProfile(
                preferredLanguages = allProfiles
                    .filter { it.key == "preferred_language" }
                    .map { it.value },
                preferredFrameworks = allProfiles
                    .filter { it.key == "preferred_framework" }
                    .map { it.value },
                codeStyle = CodeStyle(
                    indentSize = map["indent_size"]?.toIntOrNull() ?: 2,
                    useTabs = map["use_tabs"]?.toBooleanStrictOrNull() ?: false,
                    preferSingleQuotes = map["prefer_single_quotes"]?.toBooleanStrictOrNull() ?: true,
                    preferSemicolons = map["prefer_semicolons"]?.toBooleanStrictOrNull() ?: false,
                    preferFunctional = map["prefer_functional"]?.toBooleanStrictOrNull() ?: true,
                    commentLanguage = map["comment_language"] ?: "zh",
                ),
                interactionStyle = InteractionStyle(
                    verbosity = try {
                        Verbosity.valueOf(map["verbosity"] ?: "Detailed")
                    } catch (_: Exception) { Verbosity.Detailed },
                    confirmBeforeAction = map["confirm_before_action"]?.toBooleanStrictOrNull() ?: true,
                    autoSwitchModel = map["auto_switch_model"]?.toBooleanStrictOrNull() ?: true,
                    preferredModel = try {
                        ModelTier.valueOf(map["preferred_model"] ?: "V4_PRO")
                    } catch (_: Exception) { ModelTier.V4_PRO },
                ),
                workSchedule = WorkSchedule(
                    weekdayWorkModel = try {
                        ModelTier.valueOf(map["weekday_model"] ?: "V4_PRO")
                    } catch (_: Exception) { ModelTier.V4_PRO },
                    eveningModel = try {
                        ModelTier.valueOf(map["evening_model"] ?: "V4_FLASH")
                    } catch (_: Exception) { ModelTier.V4_FLASH },
                    weekendModel = try {
                        ModelTier.valueOf(map["weekend_model"] ?: "V4_FLASH")
                    } catch (_: Exception) { ModelTier.V4_FLASH },
                    nightModel = try {
                        ModelTier.valueOf(map["night_model"] ?: "V4_FLASH")
                    } catch (_: Exception) { ModelTier.V4_FLASH },
                ),
                monthlyBudget = map["monthly_budget"]?.toDoubleOrNull() ?: 50.0,
            )
        }
    }

    /**
     * Get a specific profile value.
     */
    suspend fun getProfileValue(key: String): AppResult<String?> =
        withContext(Dispatchers.IO) {
            AppResult.runCatching {
                dao.getUserProfile(key)?.value
            }
        }

    /**
     * Get all profile entries in a category.
     */
    suspend fun getProfileByCategory(
        category: String,
    ): AppResult<List<UserProfileEntity>> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            dao.getUserProfilesByCategory(category)
        }
    }

    // ── Layer 3: Semantic Memories ───────────────────────

    /**
     * Store a semantic memory — a pattern, solution, or lesson learned.
     *
     * @param content The full memory content.
     * @param summary A short (1-2 sentence) summary for search and context injection.
     * @param category Category: "pattern", "solution", "lesson", "preference".
     * @param generateEmbedding Whether to generate a simplified embedding (default: false for now).
     */
    suspend fun storeSemanticMemory(
        content: String,
        summary: String,
        category: String = "solution",
        generateEmbedding: Boolean = false,
    ): AppResult<String> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()

            val embedding = if (generateEmbedding) {
                generateSimplifiedEmbedding(summary)
            } else null

            dao.insertSemanticMemory(
                SemanticMemoryEntity(
                    id = id,
                    content = content,
                    summary = summary,
                    embeddingJson = embedding,
                    category = category,
                    createdAt = now,
                    lastRecalledAt = now,
                    recallCount = 0,
                )
            )
            id
        }
    }

    /**
     * Record that a semantic memory was recalled (used/helpful).
     * Increases recall count which improves future ranking.
     */
    suspend fun touchSemanticMemory(id: String) {
        withContext(Dispatchers.IO) {
            dao.incrementRecallCount(id)
        }
    }

    /**
     * Search across all three memory layers for information relevant to the query.
     *
     * Search strategy:
     * 1. Layer 1: Direct key/value text match in project memories
     * 2. Layer 2: Key/value text match in user profile
     * 3. Layer 3: Full-text search in semantic memories (content + summary + category)
     *
     * Results are combined and returned as [MemorySearchResult].
     */
    suspend fun searchRelevantMemories(
        query: String,
        projectId: String? = null,
        maxResultsPerLayer: Int = 10,
    ): AppResult<MemorySearchResult> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val projectMemories = if (projectId != null) {
                dao.searchProjectMemories(projectId, query, maxResultsPerLayer)
            } else {
                emptyList()
            }

            val profileEntries = dao.searchUserProfiles(query, maxResultsPerLayer)
            val semanticMemories = dao.searchSemanticMemories(query, maxResultsPerLayer)

            MemorySearchResult(
                projectMemories = projectMemories,
                profileEntries = profileEntries,
                semanticMemories = semanticMemories,
            )
        }
    }

    /**
     * Advanced multi-term search for semantic memories.
     * Splits the query into keywords and searches for memories matching any term.
     */
    suspend fun searchRelevantMemoriesAdvanced(
        query: String,
        projectId: String? = null,
    ): AppResult<MemorySearchResult> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            // Extract search terms (simple whitespace split + Chinese segmentation hint)
            val terms = query
                .split(Regex("""[\s，。！？、；：“”"''【】（）\(\)\[\]{}]+"""))
                .filter { it.length >= 2 }
                .take(5)
                .padTo(5)

            val projectMemories = if (projectId != null) {
                dao.searchProjectMemories(projectId, query, 5)
            } else {
                emptyList()
            }

            val profileEntries = if (projectId != null) {
                dao.searchUserProfiles(query, 5)
            } else {
                emptyList()
            }

            val semanticMemories = dao.searchSemanticMemoriesMultiTerm(
                term0 = terms[0],
                term1 = terms[1],
                term2 = terms[2],
                term3 = terms[3],
                term4 = terms[4],
                limit = 10,
            )

            // Boost recall count for returned memories (they were just accessed)
            semanticMemories.take(5).forEach { mem ->
                dao.incrementRecallCount(mem.id)
            }

            // Touch frequently used project memories
            projectMemories.take(3).forEach { mem ->
                dao.incrementAccessCount(mem.id)
            }

            MemorySearchResult(
                projectMemories = projectMemories,
                profileEntries = profileEntries,
                semanticMemories = semanticMemories,
            )
        }
    }

    /**
     * Get the most frequently recalled semantic memories.
     */
    suspend fun getTopSemanticMemories(
        category: String? = null,
        limit: Int = 20,
    ): AppResult<List<SemanticMemoryEntity>> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            if (category != null) {
                dao.getSemanticMemoriesByCategory(category, limit)
            } else {
                dao.getFrequentlyRecalledMemories(minRecalls = 2, limit = limit)
            }
        }
    }

    // ── Context String Builder ───────────────────────────

    /**
     * Build a compact context string for injection into the system prompt.
     *
     * Combines the most relevant information from all three memory layers
     * into a token-efficient format. Respects [AppConstants.BUDGET_USER_PROFILE].
     *
     * @param projectId Optional project ID to include project-specific memories.
     * @param currentQuery Optional user query to find relevant semantic memories.
     * @return A compact string ready for inclusion in the system prompt.
     */
    suspend fun buildContextString(
        projectId: String? = null,
        currentQuery: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        var estimatedTokens = 0L

        // Layer 2: User profile (most important, always included)
        val profiles = dao.getHighConfidenceProfiles(0.6f)
        if (profiles.isNotEmpty()) {
            sb.appendLine("[记忆: 用户偏好]")
            for (entry in profiles.take(10)) {
                val line = "- ${entry.key}: ${entry.value}\n"
                val lineTokens = (line.length / 3.5).toLong()
                if (estimatedTokens + lineTokens > AppConstants.BUDGET_USER_PROFILE) break
                sb.append(line)
                estimatedTokens += lineTokens
            }
        }

        // Layer 1: Project memories (if projectId provided)
        if (projectId != null) {
            val projectMemories = dao.getRecentMemories(projectId, 5)
            if (projectMemories.isNotEmpty()) {
                sb.appendLine("[记忆: 项目]")
                for (mem in projectMemories) {
                    val line = "- ${mem.key}: ${mem.value.take(150)}\n"
                    val lineTokens = (line.length / 3.5).toLong()
                    if (estimatedTokens + lineTokens > AppConstants.BUDGET_USER_PROFILE) break
                    sb.append(line)
                    estimatedTokens += lineTokens
                }
            }
        }

        // Layer 3: Relevant semantic memories (if query provided)
        if (currentQuery != null) {
            val match = searchRelevantMemoriesAdvanced(currentQuery, projectId)
            if (match is AppResult.Success) {
                val semantic = match.data.semanticMemories.take(3)
                if (semantic.isNotEmpty()) {
                    sb.appendLine("[记忆: 相关经验]")
                    for (mem in semantic) {
                        val line = "- ${mem.summary.take(200)}\n"
                        val lineTokens = (line.length / 3.5).toLong()
                        if (estimatedTokens + lineTokens > AppConstants.BUDGET_USER_PROFILE) break
                        sb.append(line)
                        estimatedTokens += lineTokens
                    }
                }
            }
        }

        sb.toString().trimEnd()
    }

    // ── Auto-Write from Agent Interactions ───────────────

    /**
     * Analyze an agent-user exchange and automatically extract memories.
     *
     * This is called after each agent turn to learn from the interaction.
     * It uses simple heuristics to detect:
     * - Project commands ("run ./gradlew assembleDebug")
     * - Code style hints ("use 4 spaces")
     * - User preferences ("I prefer Kotlin over Java")
     *
     * In a production system, this would be enhanced with an LLM call
     * to extract structured memories from the conversation.
     */
    suspend fun autoExtractMemories(
        projectId: String,
        userMessage: String,
        assistantResponse: String,
    ): AppResult<Int> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            var extracted = 0

            // Detect build/run commands
            val commandPatterns = listOf(
                Regex("""\./(gradlew|mvnw|npm|yarn|pnpm|cargo|go|make)\s+\S+"""),
                Regex("""(gradle|maven|npm|yarn|pnpm|cargo|go|make)\s+\S+"""),
                Regex("""(python|python3|pip|pip3)\s+\S+"""),
            )
            for (pattern in commandPatterns) {
                val matches = pattern.findAll(assistantResponse)
                for (match in matches) {
                    rememberProjectDetail(
                        projectId = projectId,
                        key = "command_${match.value.take(30).replace(Regex("""[^\w]"""), "_")}",
                        value = match.value,
                        category = "command",
                    )
                    extracted++
                }
            }

            // Detect code style mentions
            val styleHints = listOf(
                "缩进" to "code_style",
                "indent" to "code_style",
                "单引号" to "code_style",
                "双引号" to "code_style",
                "分号" to "code_style",
                "semicolon" to "code_style",
            )
            for ((keyword, category) in styleHints) {
                if (keyword in userMessage.lowercase() || keyword in assistantResponse.lowercase()) {
                    // Extract the specific preference (simplified)
                    rememberProjectDetail(
                        projectId = projectId,
                        key = "style_${keyword}",
                        value = userMessage.take(200),
                        category = category,
                    )
                    extracted++
                }
            }

            // Detect user preferences from explicit statements
            val preferencePatterns = listOf(
                Regex("""我(喜欢|习惯|常用|偏好|爱用)(\S+)"""),
                Regex("""(不要|别|不想|讨厌|不喜欢)(\S+)"""),
            )
            for (pattern in preferencePatterns) {
                pattern.find(userMessage)?.let { match ->
                    updateUserProfile(
                        key = "explicit_preference_${match.value.take(20)}",
                        value = match.value,
                        category = "interaction",
                        confidence = 0.85f,
                    )
                    extracted++
                }
            }

            extracted
        }
    }

    // ── Maintenance ──────────────────────────────────────

    /**
     * Clean up stale, low-quality memories across all layers.
     * Should be called periodically (e.g., weekly) to prevent database bloat.
     */
    suspend fun performMaintenance(): AppResult<Int> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val before = dao.getTotalProjectMemoryCount() +
                    dao.getUserProfileCount() +
                    dao.getSemanticMemoryCount()

            dao.cleanAllStaleData()

            val after = dao.getTotalProjectMemoryCount() +
                    dao.getUserProfileCount() +
                    dao.getSemanticMemoryCount()

            before - after
        }
    }

    /**
     * Get count statistics for all memory layers.
     */
    suspend fun getStats(): AppResult<MemoryStats> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            MemoryStats(
                projectMemoryCount = dao.getTotalProjectMemoryCount(),
                userProfileCount = dao.getUserProfileCount(),
                semanticMemoryCount = dao.getSemanticMemoryCount(),
            )
        }
    }

    // ── Private Helpers ──────────────────────────────────

    /**
     * Generate a simplified "embedding" for a text.
     *
     * This is NOT a real embedding — it's a lightweight hash-based representation
     * that enables basic similarity grouping. For production use, integrate with
     * a proper embedding model (e.g., via the DeepSeek API).
     *
     * Current implementation: character n-gram frequency vector (8-dim simplified).
     */
    private fun generateSimplifiedEmbedding(text: String): String? {
        if (text.isBlank()) return null

        // Simple character n-gram frequency approach
        val bigrams = text.windowed(2, 1, partialWindows = false)
            .groupBy { it }
            .mapValues { it.value.size }

        // Take top 8 bigrams by frequency as a simplified "embedding"
        val topBigrams = bigrams.entries
            .sortedByDescending { it.value }
            .take(8)

        // Encode as a simple JSON array of [hash, frequency]
        val embedding = topBigrams.map { (bigram, freq) ->
            val hash = bigram.fold(0) { acc, c -> acc * 31 + c.code } % 256
            listOf(hash, freq.coerceAtMost(255))
        }

        // Encode as JSON string manually to avoid kotlinx.serialization builtins issues
        val jsonArray = embedding.joinToString(",", "[", "]") { (hash, freq) ->
            "[$hash,$freq]"
        }
        return jsonArray
    }

    /**
     * Pad a list to at least [size] elements, filling with empty strings.
     */
    private fun List<String>.padTo(size: Int): List<String> {
        if (this.size >= size) return this
        return this + List(size - this.size) { "" }
    }
}

// ── Memory Stats ────────────────────────────────────────

/**
 * Statistics about the memory system state.
 */
data class MemoryStats(
    val projectMemoryCount: Int,
    val userProfileCount: Int,
    val semanticMemoryCount: Int,
) {
    val totalMemories: Int
        get() = projectMemoryCount + userProfileCount + semanticMemoryCount

    val isHealthy: Boolean
        get() = totalMemories < 10_000 // Arbitrary threshold before cleanup needed

    val summary: String
        get() = "项目记忆: $projectMemoryCount | 用户配置: $userProfileCount | 语义记忆: $semanticMemoryCount"
}
