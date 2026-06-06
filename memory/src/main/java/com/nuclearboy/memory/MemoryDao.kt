package com.nuclearboy.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for all memory entities in the three-layer memory system.
 *
 * Layer 1: Project-specific memories (code style, commands, structure, preferences)
 * Layer 2: User profile (languages, frameworks, style, interaction, schedule)
 * Layer 3: Semantic memories (patterns, solutions, lessons, preferences)
 */
@Dao
interface MemoryDao {

    // ── Layer 1: Project Memories ────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectMemory(memory: ProjectMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectMemories(memories: List<ProjectMemoryEntity>)

    @Update
    suspend fun updateProjectMemory(memory: ProjectMemoryEntity)

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId ORDER BY updatedAt DESC")
    suspend fun getProjectMemories(projectId: String): List<ProjectMemoryEntity>

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId AND category = :category ORDER BY updatedAt DESC")
    suspend fun getProjectMemoriesByCategory(projectId: String, category: String): List<ProjectMemoryEntity>

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId AND `key` = :key LIMIT 1")
    suspend fun getProjectMemory(projectId: String, key: String): ProjectMemoryEntity?

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId AND `key` = :key LIMIT 1")
    fun observeProjectMemory(projectId: String, key: String): Flow<ProjectMemoryEntity?>

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId ORDER BY accessCount DESC LIMIT :limit")
    suspend fun getFrequentlyAccessedMemories(projectId: String, limit: Int = 20): List<ProjectMemoryEntity>

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentMemories(projectId: String, limit: Int = 20): List<ProjectMemoryEntity>

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId AND (`value` LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchProjectMemories(projectId: String, query: String, limit: Int = 20): List<ProjectMemoryEntity>

    @Query("UPDATE project_memories SET accessCount = accessCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementAccessCount(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM project_memories WHERE projectId = :projectId AND `key` = :key")
    suspend fun deleteProjectMemory(projectId: String, key: String)

    @Query("DELETE FROM project_memories WHERE projectId = :projectId")
    suspend fun deleteAllProjectMemories(projectId: String)

    @Query("SELECT COUNT(*) FROM project_memories WHERE projectId = :projectId")
    suspend fun getProjectMemoryCount(projectId: String): Int

    @Query("SELECT COUNT(*) FROM project_memories")
    suspend fun getTotalProjectMemoryCount(): Int

    @Query("SELECT DISTINCT category FROM project_memories WHERE projectId = :projectId")
    suspend fun getProjectMemoryCategories(projectId: String): List<String>

    // ── Layer 2: User Profile ────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfiles(profiles: List<UserProfileEntity>)

    @Update
    suspend fun updateUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE `key` = :key LIMIT 1")
    suspend fun getUserProfile(key: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE `key` = :key LIMIT 1")
    fun observeUserProfile(key: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile ORDER BY updatedAt DESC")
    suspend fun getAllUserProfiles(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile WHERE category = :category ORDER BY confidence DESC")
    suspend fun getUserProfilesByCategory(category: String): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getHighConfidenceProfiles(minConfidence: Float = 0.7f): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile WHERE (`value` LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%') ORDER BY confidence DESC LIMIT :limit")
    suspend fun searchUserProfiles(query: String, limit: Int = 10): List<UserProfileEntity>

    @Query("UPDATE user_profile SET confidence = :confidence, updatedAt = :now WHERE `key` = :key")
    suspend fun updateProfileConfidence(key: String, confidence: Float, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_profile WHERE `key` = :key")
    suspend fun deleteUserProfile(key: String)

    @Query("DELETE FROM user_profile WHERE confidence < :threshold")
    suspend fun pruneLowConfidenceProfiles(threshold: Float = 0.3f)

    @Query("SELECT COUNT(*) FROM user_profile")
    suspend fun getUserProfileCount(): Int

    @Query("SELECT DISTINCT category FROM user_profile")
    suspend fun getUserProfileCategories(): List<String>

    // ── Layer 3: Semantic Memories ───────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemanticMemory(memory: SemanticMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemanticMemories(memories: List<SemanticMemoryEntity>)

    @Update
    suspend fun updateSemanticMemory(memory: SemanticMemoryEntity)

    @Query("SELECT * FROM semantic_memories WHERE id = :id LIMIT 1")
    suspend fun getSemanticMemory(id: String): SemanticMemoryEntity?

    @Query("SELECT * FROM semantic_memories ORDER BY lastRecalledAt DESC LIMIT :limit")
    suspend fun getRecentSemanticMemories(limit: Int = 50): List<SemanticMemoryEntity>

    @Query("SELECT * FROM semantic_memories WHERE category = :category ORDER BY recallCount DESC LIMIT :limit")
    suspend fun getSemanticMemoriesByCategory(category: String, limit: Int = 30): List<SemanticMemoryEntity>

    @Query("SELECT * FROM semantic_memories WHERE recallCount >= :minRecalls ORDER BY recallCount DESC LIMIT :limit")
    suspend fun getFrequentlyRecalledMemories(minRecalls: Int = 3, limit: Int = 30): List<SemanticMemoryEntity>

    /**
     * Full-text search across semantic memories.
     * Matches against content, summary, and category.
     */
    @Query(
        """
        SELECT * FROM semantic_memories
        WHERE content LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY recallCount DESC, lastRecalledAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchSemanticMemories(query: String, limit: Int = 20): List<SemanticMemoryEntity>

    /**
     * Multi-term search: find memories that match any of the given terms.
     * Each term is OR'd together, with more specific matches ranked higher.
     */
    @Query(
        """
        SELECT * FROM semantic_memories
        WHERE (content LIKE '%' || :term0 || '%' OR summary LIKE '%' || :term0 || '%' OR category LIKE '%' || :term0 || '%')
           OR (content LIKE '%' || :term1 || '%' OR summary LIKE '%' || :term1 || '%' OR category LIKE '%' || :term1 || '%')
           OR (content LIKE '%' || :term2 || '%' OR summary LIKE '%' || :term2 || '%' OR category LIKE '%' || :term2 || '%')
           OR (content LIKE '%' || :term3 || '%' OR summary LIKE '%' || :term3 || '%' OR category LIKE '%' || :term3 || '%')
           OR (content LIKE '%' || :term4 || '%' OR summary LIKE '%' || :term4 || '%' OR category LIKE '%' || :term4 || '%')
        ORDER BY recallCount DESC, lastRecalledAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchSemanticMemoriesMultiTerm(
        term0: String, term1: String, term2: String,
        term3: String, term4: String, limit: Int = 20,
    ): List<SemanticMemoryEntity>

    @Query("UPDATE semantic_memories SET recallCount = recallCount + 1, lastRecalledAt = :now WHERE id = :id")
    suspend fun incrementRecallCount(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM semantic_memories WHERE id = :id")
    suspend fun deleteSemanticMemory(id: String)

    @Query("DELETE FROM semantic_memories WHERE recallCount = 0 AND lastRecalledAt < :olderThan")
    suspend fun pruneStaleMemories(olderThan: Long = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)

    @Query("SELECT COUNT(*) FROM semantic_memories")
    suspend fun getSemanticMemoryCount(): Int

    @Query("SELECT COUNT(*) FROM semantic_memories WHERE category = :category")
    suspend fun getSemanticMemoryCountByCategory(category: String): Int

    @Query("SELECT DISTINCT category FROM semantic_memories")
    suspend fun getSemanticMemoryCategories(): List<String>

    // ── Maintenance ──────────────────────────────────────

    @Query("DELETE FROM project_memories WHERE updatedAt < :olderThan AND accessCount < :minAccess")
    suspend fun cleanOldProjectMemories(
        olderThan: Long = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000,
        minAccess: Int = 2,
    )

    @Transaction
    suspend fun cleanAllStaleData() {
        cleanOldProjectMemories()
        pruneLowConfidenceProfiles()
        pruneStaleMemories()
    }

    companion object {
        /**
         * Build a multi-term WHERE clause for semantic memory search.
         * Generates: content LIKE '%' || :term0 || '%' OR summary LIKE '%' || :term0 || '%' ...
         */
        private fun buildMultiTermWhere(termCount: Int): String {
            val terms = (0 until termCount).joinToString(" OR ") { i ->
                "(content LIKE '%' || :term$i || '%' OR summary LIKE '%' || :term$i || '%' OR category LIKE '%' || :term$i || '%')"
            }
            return terms
        }
    }
}
