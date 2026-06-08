package com.nuclearboy.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nuclearboy.app.update.UpdateManager
import com.nuclearboy.memory.MemoryStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * NUCLEAR BOY Application entry point.
 * Initializes Python runtime, logging, DI, and update checker.
 */
@HiltAndroidApp
class NuclearBoyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.e("NuclearBoy", "[App] onCreate — NUCLEAR BOY starting")

        // Initialize Timber for logging
        if (com.nuclearboy.app.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize Chaquopy Python runtime
        try {
            if (!Python.isStarted()) {
                android.util.Log.e("NuclearBoy", "[App] onCreate — starting Chaquopy Python")
                Python.start(AndroidPlatform(this))
            }
        } catch (e: Exception) {
            Timber.e(e, "Python runtime unavailable — Python features disabled")
            android.util.Log.e("NuclearBoy", "[App] onCreate — Python init FAILED: ${e.message}")
        }

        // Copy built-in skills from assets to internal storage
        copyBuiltinSkills()

        // 后台检查更新
        val updateManager = UpdateManager(this)
        appScope.launch {
            try {
                updateManager.autoCheck()
            } catch (e: Exception) {
                android.util.Log.e("NuclearBoy", "[App] autoCheck FAILED: ${e.message}")
            }
        }

        // 记忆系统：启动时读取上次会话，推送欢迎通知
        val memoryStore = if (BuildConfig.DEBUG) MemoryStore(this) else null // Hilt will provide in ViewModels
        appScope.launch {
            try {
                val lastProject = memoryStore?.getProfileValue("last_project")
                val lastName = (lastProject as? com.nuclearboy.common.AppResult.Success)?.data
                if (!lastName.isNullOrBlank() && lastName != "default") {
                    showWelcomeNotification(lastName)
                }
            } catch (e: Exception) {
                android.util.Log.e("NuclearBoy", "[App] memory check FAILED: ${e.message}")
            }
        }

        if (com.nuclearboy.app.BuildConfig.DEBUG) {
            Timber.d("☢️ NUCLEAR BOY started — ready to code!")
        }
        android.util.Log.e("NuclearBoy", "[App] onCreate — completed")
    }

    override fun onTerminate() {
        super.onTerminate()
        instance = null
    }

    private fun copyBuiltinSkills() {
        try {
            val skillsDir = java.io.File(filesDir, "skills")
            val markerFile = java.io.File(skillsDir, ".builtin_installed")
            if (markerFile.exists()) {
                android.util.Log.e("NuclearBoy", "[App] copyBuiltinSkills — marker exists, skipping")
                return // Already copied
            }

            val assetSkills = assets.list("skills") ?: run {
                android.util.Log.e("NuclearBoy", "[App] copyBuiltinSkills — no skills in assets")
                return
            }
            android.util.Log.e("NuclearBoy", "[App] copyBuiltinSkills — found ${assetSkills.size} skills in assets")
            for (skillName in assetSkills) {
                val skillDir = java.io.File(skillsDir, skillName)
                skillDir.mkdirs()
                val assetSkillPath = "skills/$skillName"
                val skillFiles = assets.list(assetSkillPath) ?: continue
                for (fileName in skillFiles) {
                    val input = assets.open("$assetSkillPath/$fileName")
                    val outputFile = java.io.File(skillDir, fileName)
                    input.use { it.copyTo(outputFile.outputStream()) }
                }
                android.util.Log.e("NuclearBoy", "[App] copyBuiltinSkills — copied skill '$skillName' (${skillFiles.size} files)")
                Timber.d("Installed built-in skill: $skillName")
            }
            markerFile.createNewFile()
            android.util.Log.e("NuclearBoy", "[App] copyBuiltinSkills — marker created, skillsDir=${skillsDir.absolutePath}")
            Timber.d("Built-in skills installed to: ${skillsDir.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy built-in skills")
            android.util.Log.e("NuclearBoy", "[App] copyBuiltinSkills FAILED — ${e.message}")
        }
    }

    private fun showWelcomeNotification(projectName: String) {
        try {
            val channelId = "welcome_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "欢迎回来", NotificationManager.IMPORTANCE_DEFAULT)
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
            val intent = Intent(this, MainActivity::class.java)
            val pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("☢️ 欢迎回来！")
                .setContentText("上次在搞「$projectName」，要继续吗？")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(3000, notification)
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: NuclearBoyApp? = null

        fun getInstance(): NuclearBoyApp {
            return instance ?: throw IllegalStateException(
                "NuclearBoyApp not initialized"
            )
        }
    }
}
