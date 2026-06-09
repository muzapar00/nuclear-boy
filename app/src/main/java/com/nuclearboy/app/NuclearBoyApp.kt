package com.nuclearboy.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.nuclearboy.app.update.UpdateManager
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

        // 记忆系统：Hilt管理的MemoryStore在ChatViewModel中使用，此处不重复创建

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
