package com.nuclearboy.app.python

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.nuclearboy.python.PythonExecutor
import com.nuclearboy.python.PythonResult
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Real Python executor backed by Chaquopy's embedded CPython runtime.
 *
 * This is created in the :app module because Chaquopy API classes
 * are only available where the Chaquopy Gradle plugin is applied.
 */
class ChaquopyPythonExecutor : PythonExecutor {

    private val installedPackages = ConcurrentHashMap.newKeySet<String>()

    override fun start(context: Context) {
        android.util.Log.e("NuclearBoy", "[Chaquopy] start — entry")
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        Timber.d("☢️ Chaquopy Python runtime started")

        // Discover pre-installed packages
        try {
            val py = Python.getInstance()
            val pkgResources = py.getModule("pkg_resources")
            val workingSet = pkgResources.callAttr("working_set")
            var count = 0
            for (pkg in workingSet.asList()) {
                installedPackages.add(pkg.callAttr("project_name").toString())
                count++
            }
            android.util.Log.e("NuclearBoy", "[Chaquopy] start — enumerated $count installed packages")
        } catch (e: Exception) {
            Timber.w(e, "Failed to enumerate installed packages")
            android.util.Log.e("NuclearBoy", "[Chaquopy] start — failed to enumerate packages: ${e.message}")
        }
    }

    override fun isStarted(): Boolean = Python.isStarted()

    override fun run(
        script: String,
        workingDir: String,
        env: Map<String, String>,
    ): PythonResult {
        val startTime = System.currentTimeMillis()
        android.util.Log.e("NuclearBoy", "[Chaquopy] run — scriptLen=${script.length}, workingDir=$workingDir, envKeys=${env.keys}")
        return try {
            val py = Python.getInstance()

            // Write output to known files — most reliable approach for Chaquopy
            val outFile = java.io.File.createTempFile("nb_out_", ".txt")
            val errFile = java.io.File.createTempFile("nb_err_", ".txt")
            val outPath = outFile.absolutePath.replace("\\", "/")
            val errPath = errFile.absolutePath.replace("\\", "/")
            android.util.Log.e("NuclearBoy", "[Chaquopy] run — temp outFile=$outPath, errFile=$errPath")

            val wrappedScript = buildString {
                appendLine("import sys, os, traceback")
                // Add skills directory to sys.path so skill modules are importable
                appendLine("try:")
                appendLine("    from com.chaquo.python import Python")
                appendLine("    _skills_dir = Python.getPlatform().getApplication().getFilesDir().getAbsolutePath() + '/skills'")
                appendLine("    if _skills_dir not in sys.path: sys.path.insert(0, _skills_dir)")
                appendLine("except: pass")
                // Safe escaping: replace backslashes and single quotes to prevent
                // Python string injection from malicious paths
                if (workingDir.isNotBlank()) {
                    val safeDir = workingDir.replace("\\", "\\\\").replace("'", "\\'")
                    appendLine("os.chdir('$safeDir')")
                }
                env.forEach { (key, value) ->
                    val safeKey = key.replace("\\", "\\\\").replace("'", "\\'")
                    val safeValue = value.replace("\\", "\\\\").replace("'", "\\'")
                    appendLine("os.environ['$safeKey'] = '$safeValue'")
                }
                appendLine()
                // Open output files, overwrite any old content
                appendLine("_out_fp = open('$outPath', 'w', encoding='utf-8')")
                appendLine("_err_fp = open('$errPath', 'w', encoding='utf-8')")
                appendLine("_out_fp.write('')  # Touch file")
                appendLine("_err_fp.write('')")
                appendLine()
                // Replace sys.stdout/stderr with our file handles
                appendLine("_old_out = sys.stdout")
                appendLine("_old_err = sys.stderr")
                appendLine("sys.stdout = _out_fp")
                appendLine("sys.stderr = _err_fp")
                appendLine("_nb_exit_code = 0")
                appendLine("try:")
                for (line in script.lines()) {
                    appendLine("    $line")
                }
                appendLine("except SystemExit as e:")
                appendLine("    _nb_exit_code = e.code if e.code is not None else 0")
                appendLine("except:")
                appendLine("    traceback.print_exc()")
                appendLine("    _nb_exit_code = 1")
                appendLine("finally:")
                appendLine("    _out_fp.flush()")
                appendLine("    _err_fp.flush()")
                appendLine("    _out_fp.close()")
                appendLine("    _err_fp.close()")
                appendLine("    sys.stdout = _old_out")
                appendLine("    sys.stderr = _old_err")
            }

            android.util.Log.e("NuclearBoy", "[Chaquopy] run — wrappedScript size=${wrappedScript.length} chars")

            val locals = py.getModule("builtins").callAttr("dict")
            locals.callAttr("__setitem__", "__name__", "__main__")  // Fix: exec() runs as __main__
            py.getModule("builtins").callAttr("exec", wrappedScript, locals)

            val exitCode = (locals.callAttr("get", "_nb_exit_code", 0) as? Int) ?: 0

            // Read output directly from files (bypass exec() dict)
            val stdout = try { outFile.readText() } catch (_: Exception) { "" }
            val stderr = try { errFile.readText() } catch (_: Exception) { "" }

            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("NuclearBoy", "[Chaquopy] run — duration=${duration}ms, exitCode=$exitCode, stdoutLen=${stdout.length}, stderrLen=${stderr.length}")
            android.util.Log.e("NuclearBoy", "🐍 subprocess: exit=$exitCode out='${stdout.take(200)}' err='${stderr.take(200)}'")

            // Clean up
            try { outFile.delete() } catch (_: Exception) {}
            try { errFile.delete() } catch (_: Exception) {}
            android.util.Log.e("NuclearBoy", "[Chaquopy] run — cleaned up temp files")

            PythonResult(exitCode, stdout, stderr, duration)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("NuclearBoy", "[Chaquopy] run — EXCEPTION after ${duration}ms: ${e.message}")
            PythonResult.failure(
                e.message ?: "Chaquopy 执行错误",
                duration,
            )
        }
    }

    override fun installPackage(packageName: String): Boolean {
        android.util.Log.e("NuclearBoy", "[Chaquopy] installPackage — name=$packageName")
        return try {
            val pip = Python.getInstance().getModule("pip")
            pip.callAttr("main", listOf("install", packageName))
            installedPackages.add(packageName)
            android.util.Log.e("NuclearBoy", "[Chaquopy] installPackage SUCCESS — $packageName")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to pip install $packageName")
            android.util.Log.e("NuclearBoy", "[Chaquopy] installPackage FAILED — $packageName, error=${e.message}")
            false
        }
    }

    override fun getVersion(): String {
        return try {
            val sys = Python.getInstance().getModule("sys")
            val version = "Python ${sys.callAttr("version")}"
            android.util.Log.e("NuclearBoy", "[Chaquopy] getVersion — $version")
            version
        } catch (e: Exception) {
            android.util.Log.e("NuclearBoy", "[Chaquopy] getVersion — fallback (Chaquopy not started)")
            "Python 3.11 (Chaquopy)"
        }
    }

    override fun getInstalledPackages(): Set<String> = installedPackages.toSet()
}
