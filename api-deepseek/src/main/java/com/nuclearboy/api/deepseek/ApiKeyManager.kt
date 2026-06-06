package com.nuclearboy.api.deepseek

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nuclearboy.common.AppError
import com.nuclearboy.common.AppResult
import com.nuclearboy.common.maskApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Secure API key management using Android Keystore-backed encryption.
 *
 * Features:
 * - Encrypted storage via EncryptedSharedPreferences
 * - Primary + backup key support
 * - Automatic failover when primary key is exhausted
 * - Balance checking and validation
 * - Safe display (masked) in UI
 */
class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "nuclearboy_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    data class ApiKeyState(
        val hasPrimaryKey: Boolean = false,
        val primaryKeyMasked: String = "",
        val hasBackupKey: Boolean = false,
        val backupKeyMasked: String = "",
        val balanceCny: String? = null,
        val isValidated: Boolean = false,
        val lastValidatedAt: Long = 0,
        val autoSwitchEnabled: Boolean = true,
        val simpleTasksUseFlash: Boolean = true,
    )

    private val _state = MutableStateFlow(ApiKeyState())
    val state: StateFlow<ApiKeyState> = _state.asStateFlow()

    private val primaryKey: String?
        get() = prefs.getString(KEY_PRIMARY, null)

    private val backupKey: String?
        get() = prefs.getString(KEY_BACKUP, null)

    init {
        refreshState()
    }

    /**
     * Get the active API key. Returns primary if available, falls back to backup.
     */
    fun getActiveKey(): String? {
        return primaryKey ?: backupKey
    }

    /**
     * Store and validate the primary API key.
     */
    suspend fun setPrimaryKey(apiKey: String): AppResult<Boolean> {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(AppError.ApiKeyInvalid, "API Key 不能为空")
        }
        if (!trimmed.startsWith("sk-")) {
            return AppResult.failure(
                AppError.ApiKeyInvalid,
                "API Key 格式不正确，应该以 sk- 开头"
            )
        }

        return withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_PRIMARY, trimmed).apply()
            refreshState()
            AppResult.success(true)
        }
    }

    /**
     * Store a backup API key.
     */
    suspend fun setBackupKey(apiKey: String): AppResult<Boolean> {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(AppError.ApiKeyInvalid, "备用 Key 不能为空")
        }

        return withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_BACKUP, trimmed).apply()
            refreshState()
            AppResult.success(true)
        }
    }

    /**
     * Switch to backup key (called when primary is exhausted).
     */
    fun switchToBackup(): Boolean {
        val backup = backupKey ?: return false
        prefs.edit()
            .putString(KEY_PRIMARY, backup)
            .remove(KEY_BACKUP)
            .apply()
        refreshState()
        return true
    }

    /**
     * Remove a key.
     */
    suspend fun removeKey(isPrimary: Boolean) {
        withContext(Dispatchers.IO) {
            if (isPrimary) {
                prefs.edit().remove(KEY_PRIMARY).apply()
            } else {
                prefs.edit().remove(KEY_BACKUP).apply()
            }
            refreshState()
        }
    }

    /**
     * Update balance display.
     */
    fun updateBalance(cny: String?) {
        _state.value = _state.value.copy(
            balanceCny = cny,
            isValidated = cny != null,
            lastValidatedAt = if (cny != null) System.currentTimeMillis()
                else _state.value.lastValidatedAt,
        )
    }

    /**
     * Toggle auto-switch behavior.
     */
    fun setAutoSwitch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SWITCH, enabled).apply()
        _state.value = _state.value.copy(autoSwitchEnabled = enabled)
    }

    fun setSimpleTasksUseFlash(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMPLE_FLASH, enabled).apply()
        _state.value = _state.value.copy(simpleTasksUseFlash = enabled)
    }

    private fun refreshState() {
        val primary = primaryKey
        val backup = backupKey
        _state.value = ApiKeyState(
            hasPrimaryKey = primary != null,
            primaryKeyMasked = primary?.maskApiKey() ?: "",
            hasBackupKey = backup != null,
            backupKeyMasked = backup?.maskApiKey() ?: "",
            balanceCny = _state.value.balanceCny,
            isValidated = _state.value.isValidated,
            lastValidatedAt = _state.value.lastValidatedAt,
            autoSwitchEnabled = prefs.getBoolean(KEY_AUTO_SWITCH, true),
            simpleTasksUseFlash = prefs.getBoolean(KEY_SIMPLE_FLASH, true),
        )
    }

    companion object {
        private const val KEY_PRIMARY = "api_key_primary"
        private const val KEY_BACKUP = "api_key_backup"
        private const val KEY_AUTO_SWITCH = "auto_switch_enabled"
        private const val KEY_SIMPLE_FLASH = "simple_tasks_use_flash"
    }
}
