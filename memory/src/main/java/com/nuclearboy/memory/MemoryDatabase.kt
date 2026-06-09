package com.nuclearboy.memory

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ── Entities ────────────────────────────────────────────

/**
 * Layer 1: Project-specific memories.
 *
 * Stores information learned about a specific project:
 * - Code style conventions
 * - Common build/run commands
 * - Project structure knowledge
 * - User preferences for this project
 */
@Entity(
    tableName = "project_memories",
    indices = [
        Index(value = ["projectId", "key"], unique = true),
        Index(value = ["projectId", "category"]),
    ]
)
data class ProjectMemoryEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val key: String,
    val value: String,
    val category: String, // "code_style", "command", "structure", "preference"
    val createdAt: Long,
    val updatedAt: Long,
    val accessCount: Int = 0,
)

/**
 * Layer 2: User profile preferences.
 *
 * Stores everything the agent learns about the user:
 * - Preferred programming languages
 * - Preferred frameworks and libraries
 * - Code style preferences
 * - Interaction preferences
 * - Work schedule patterns
 *
 * Each entry has a confidence score (0.0 - 1.0) indicating how sure
 * we are about this preference. Confidence increases with repeated
 * observation and decreases when contradicted.
 */
@Entity(
    tableName = "user_profile",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["category"]),
    ]
)
data class UserProfileEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val category: String, // "language", "framework", "style", "interaction", "schedule"
    val confidence: Float, // 0.0 - 1.0
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Layer 3: Semantic memories.
 *
 * Higher-level patterns, solutions, and lessons learned across projects.
 * These are more abstract than project memories and can be recalled
 * based on similarity to the current task.
 *
 * The embeddingJson field stores a simplified embedding vector as JSON
 * for similarity-based retrieval. For now, text-based search is the
 * primary retrieval method.
 */
@Entity(
    tableName = "semantic_memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["recallCount"]),
        Index(value = ["lastRecalledAt"]),
    ]
)
data class SemanticMemoryEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val summary: String,
    val embeddingJson: String?, // JSON-encoded FloatArray (simplified)
    val category: String, // "pattern", "solution", "lesson", "preference"
    val createdAt: Long,
    val lastRecalledAt: Long,
    val recallCount: Int = 0,
)

// ── Database ────────────────────────────────────────────

/**
 * Room database for the three-layer memory system.
 *
 * Schema:
 * - project_memories (Layer 1): Project-specific facts and preferences
 * - user_profile (Layer 2): User-level preferences and patterns
 * - semantic_memories (Layer 3): Cross-project knowledge and patterns
 *
 * Database location: app's internal storage (not user-accessible).
 * Migrations are handled gracefully — on destructive migration, only
 * semantic memories are rebuilt; project and profile data is preserved.
 */
@Database(
    entities = [
        ProjectMemoryEntity::class,
        UserProfileEntity::class,
        SemanticMemoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DATABASE_NAME = "nuclearboy_memory.db"

        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * @param context Application context (not Activity context).
         * @return The singleton [MemoryDatabase] instance.
         */
        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): MemoryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java,
                DATABASE_NAME,
            )
                .addCallback(DatabaseCallback())
                // Explicit migrations only — no destructive fallback.
                // When adding a new schema version, add a Migration object below.
                .build()
        }

        /**
         * Migration examples for future schema changes.
         * Add new Migration objects here and append to the builder chain.
         *
         * Usage:
         *   val MIGRATION_1_2 = object : Migration(1, 2) {
         *       override fun migrate(db: SupportSQLiteDatabase) {
         *           db.execSQL("ALTER TABLE ...")
         *       }
         *   }
         *   Then add .addMigrations(MIGRATION_1_2) to the builder.
         */

        /**
         * Close and release the database instance.
         * Call during app shutdown.
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }

    /**
     * Callback invoked when the database is first created.
     * No seed data needed — all data is learned through agent interactions.
     */
    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Database is created empty — no seed data.
            // The agent will populate it as it learns about the user and projects.
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Enable WAL mode for better concurrent read performance
            db.execSQL("PRAGMA journal_mode=WAL")
            db.execSQL("PRAGMA synchronous=NORMAL")
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }
}
