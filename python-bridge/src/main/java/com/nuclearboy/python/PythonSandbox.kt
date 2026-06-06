package com.nuclearboy.python

import android.content.Context
import android.util.Log
import com.nuclearboy.common.AppConstants
import com.nuclearboy.common.AppError
import com.nuclearboy.common.AppResult
import com.nuclearboy.common.FileChange
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of a Python script execution.
 */
data class PythonResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTimeMs: Long,
    val fileChanges: List<FileChange> = emptyList(),
    val truncatedStdout: Boolean = false,
    val truncatedStderr: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0
    val hasError: Boolean get() = stderr.isNotBlank()

    fun displayOutput(maxChars: Int = AppConstants.PYTHON_MAX_OUTPUT_CHARS.toInt()): String {
        return if (stdout.length > maxChars) {
            stdout.take(maxChars) + "\n\n... (输出过长，已截断 ${stdout.length - maxChars} 字符)"
        } else {
            stdout
        }
    }

    companion object {
        fun success(stdout: String, executionTimeMs: Long, fileChanges: List<FileChange> = emptyList()): PythonResult =
            PythonResult(0, stdout, "", executionTimeMs, fileChanges)

        fun failure(stderr: String, executionTimeMs: Long): PythonResult =
            PythonResult(1, "", stderr, executionTimeMs)
    }
}

/**
 * Low-level Python execution interface.
 *
 * Implementations handle the actual CPython runtime (Chaquopy or stub).
 * This is an internal SPI — consumers use [PythonSandbox].
 */
interface PythonExecutor {
    fun start(context: Context)
    fun isStarted(): Boolean
    fun run(script: String, workingDir: String, env: Map<String, String>): PythonResult
    fun installPackage(packageName: String): Boolean
    fun getVersion(): String
    fun getInstalledPackages(): Set<String>
}

/**
 * Python sandbox for running Python code on Android.
 *
 * Delegates actual CPython calls to a pluggable [PythonExecutor].
 * When Chaquopy is available (app module injects the real executor),
 * this runs real Python. Otherwise it returns a friendly error.
 *
 * Public API is stable — callers don't change when the executor is swapped.
 */
class PythonSandbox(private val context: Context) {

    private val initialized = AtomicBoolean(false)

    /**
     * Inject the real Python executor. Called by the app module after Hilt init.
     * If never called, the sandbox operates in stub mode.
     */
    @Volatile
    var executor: PythonExecutor? = null

    // ── Initialization ───────────────────────────────────

    fun initialize(): AppResult<Boolean> {
        if (initialized.get()) return AppResult.success(true)
        return try {
            executor?.start(context)
            initialized.set(true)
            AppResult.success(true)
        } catch (e: Exception) {
            AppResult.failure(AppError.PythonRuntimeError, "Python 启动失败: ${e.message}")
        }
    }

    fun isInitialized(): Boolean = initialized.get()

    // ── Script Execution ─────────────────────────────────

    suspend fun execute(
        script: String,
        workingDir: String,
        timeoutSeconds: Long = AppConstants.PYTHON_EXECUTION_TIMEOUT_SECONDS,
        env: Map<String, String> = emptyMap(),
    ): PythonResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        val startTime = System.currentTimeMillis()

        return@withContext try {
            withTimeout(timeoutSeconds * 1000) {
                val exec = executor
                android.util.Log.e("NuclearBoy", "🐍 PythonSandbox: executor=${exec != null} started=${exec?.isStarted()}")
                if (exec != null && exec.isStarted()) {
                    val result = exec.run(script, workingDir, env)
                    android.util.Log.e("NuclearBoy", "🐍 PythonSandbox result: exit=${result.exitCode} out='${result.stdout}' err='${result.stderr}'")
                    result
                } else {
                    PythonResult.failure(
                        "Python 运行时未就绪。请安装 Chaquopy 插件以启用 Python 执行。",
                        System.currentTimeMillis() - startTime,
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            PythonResult.failure(
                "Python 执行超时 (${timeoutSeconds}秒)",
                System.currentTimeMillis() - startTime,
            )
        } catch (e: Exception) {
            PythonResult.failure(
                e.message ?: "未知 Python 错误",
                System.currentTimeMillis() - startTime,
            )
        }
    }

    suspend fun executeScriptFile(
        scriptPath: String,
        workingDir: String,
        args: List<String> = emptyList(),
    ): PythonResult = withContext(Dispatchers.IO) {
        val scriptFile = File(scriptPath)
        if (!scriptFile.exists()) {
            return@withContext PythonResult.failure("脚本文件不存在: $scriptPath", 0)
        }
        val script = buildString {
            appendLine("import sys")
            appendLine("sys.argv = ['$scriptPath'${args.joinToString("") { ", '$it'" }}]")
            appendLine(scriptFile.readText())
        }
        execute(script, workingDir)
    }

    // ── Package Management ───────────────────────────────

    suspend fun installPackage(packageName: String): AppResult<Boolean> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            try {
                val exec = executor
                if (exec != null && exec.isStarted()) {
                    exec.installPackage(packageName)
                    AppResult.success(true)
                } else {
                    AppResult.failure(AppError.PythonPackageError, "Python 运行时未就绪")
                }
            } catch (e: Exception) {
                AppResult.failure(
                    AppError.PythonPackageError,
                    "安装 ${packageName} 失败: ${e.message}",
                )
            }
        }

    fun isPackageAvailable(packageName: String): Boolean =
        executor?.getInstalledPackages()?.contains(packageName) ?: false

    fun getInstalledPackages(): Set<String> = executor?.getInstalledPackages() ?: emptySet()

    fun getPythonVersion(): String = executor?.getVersion() ?: "Python 3.11 (Chaquopy — 未初始化)"

    // ── Internal ─────────────────────────────────────────

    private fun ensureInitialized() {
        if (!initialized.get()) {
            initialize()
        }
    }
}
