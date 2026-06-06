package com.nuclearboy.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuclearboy.common.Project
import com.nuclearboy.common.SkillInfo
import com.nuclearboy.skills.SkillManager
import com.nuclearboy.tools.docgen.FileOperations
import kotlinx.serialization.serializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val fileOperations: FileOperations,
    private val skillManager: SkillManager,
) : ViewModel() {
    val activeSkills: StateFlow<List<SkillInfo>> = skillManager.activeSkills

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    init {
        android.util.Log.e("NuclearBoy", "[ProjectVM] ProjectViewModel created")
        loadProjects()
    }

    fun createProject(name: String) {
        android.util.Log.e("NuclearBoy", "[ProjectVM] createProject — name=$name")
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) {
                fileOperations.createProject(name)
            }) {
                is com.nuclearboy.common.AppResult.Success -> {
                    val project = result.data // Keep UUID from FileOperations
                    android.util.Log.e("NuclearBoy", "[ProjectVM] createProject SUCCESS — id=${project.id}, name=${project.name}")
                    _projects.value = _projects.value + project
                    // Persist project metadata for future loading
                    persistProjectMeta(project)
                }
                is com.nuclearboy.common.AppResult.Failure -> {
                    android.util.Log.e("NuclearBoy", "[ProjectVM] createProject FAILED — ${result.error.humanMessage}")
                    // TODO: show error
                }
            }
        }
    }

    fun selectProject(projectId: String) {
        val project = _projects.value.find { it.id == projectId }
        if (project != null) {
            android.util.Log.e("NuclearBoy", "[ProjectVM] selectProject — projectId=$projectId, name=${project.name} (found)")
        } else {
            android.util.Log.e("NuclearBoy", "[ProjectVM] selectProject — projectId=$projectId (NOT found in ${_projects.value.size} projects)")
        }
        fileOperations.currentProjectDir = project?.name ?: projectId
    }

    fun deleteProject(projectId: String) {
        android.util.Log.e("NuclearBoy", "[ProjectVM] deleteProject — projectId=$projectId")
        val project = _projects.value.find { it.id == projectId } ?: return
        viewModelScope.launch {
            // Must clear currentProjectDir so deleteFile resolves relative to workspaceRoot,
            // not workspaceRoot/projectName/projectName (double-nested)
            val saved = fileOperations.currentProjectDir
            fileOperations.currentProjectDir = ""
            val result = withContext(Dispatchers.IO) { fileOperations.deleteFile(project.name) }
            fileOperations.currentProjectDir = saved
            if (result is com.nuclearboy.common.AppResult.Success) {
                _projects.value = _projects.value.filter { it.id != projectId }
                android.util.Log.e("NuclearBoy", "[ProjectVM] deleteProject SUCCESS: ${project.name}")
            } else {
                val err = (result as com.nuclearboy.common.AppResult.Failure).error.humanMessage
                android.util.Log.e("NuclearBoy", "[ProjectVM] deleteProject FAILED: ${project.name} — $err")
            }
        }
    }

    private fun loadProjects() {
        android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects — entry")
        viewModelScope.launch {
            // Load from workspace root (NOT current project dir)
            val result = withContext(Dispatchers.IO) {
                // Save and restore currentProjectDir to avoid contamination
                val saved = fileOperations.currentProjectDir
                fileOperations.currentProjectDir = ""
                val workspaceRoot = fileOperations.getWorkspaceRoot()
                android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects — workspaceRoot=$workspaceRoot")
                val r = fileOperations.listDirectory(".")
                fileOperations.currentProjectDir = saved
                r
            }
            when (result) {
                is com.nuclearboy.common.AppResult.Success -> {
                    val projectDirs = result.data.filter { it.isDirectory }
                    android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects — found ${projectDirs.size} directories")
                    _projects.value = projectDirs.mapNotNull { dir ->
                        // Try to load persisted project metadata
                        val meta = loadProjectMeta(dir.name)
                        if (meta != null) {
                            android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects — loaded meta for '${dir.name}', id=${meta.id}")
                            meta.copy(
                                rootPath = dir.path,
                                lastOpenedAt = dir.lastModified,
                            )
                        } else {
                            // Legacy project without metadata — create it now
                            val project = Project(
                                name = dir.name,
                                rootPath = dir.path,
                                lastOpenedAt = dir.lastModified,
                                fileCount = 0,
                            )
                            android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects — legacy project '${dir.name}', created meta id=${project.id}")
                            persistProjectMeta(project)
                            project
                        }
                    }
                    android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects — final count=${_projects.value.size}")
                }
                is com.nuclearboy.common.AppResult.Failure -> {
                    android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjects FAILED — ${result.error.humanMessage}")
                    // Empty or error
                }
            }
        }
    }

    private fun persistProjectMeta(project: Project) {
        try {
            val metaDir = java.io.File(fileOperations.projectRoot(), ".agent")
            // projectRoot() uses currentProjectDir which may be empty — use workspaceRoot directly
            val projectDir = java.io.File(fileOperations.getWorkspaceRoot(), project.name)
            val agentDir = java.io.File(projectDir, ".agent")
            agentDir.mkdirs()
            val metaFile = java.io.File(agentDir, "project.json")
            val json = kotlinx.serialization.json.Json { encodeDefaults = true }
            metaFile.writeText(json.encodeToString(Project.serializer(), project))
            android.util.Log.e("NuclearBoy", "[ProjectVM] persistProjectMeta — name=${project.name}, id=${project.id}, path=${metaFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("NuclearBoy", "[ProjectVM] persistProjectMeta FAILED — ${e.message}")
        }
    }

    private fun loadProjectMeta(dirName: String): Project? {
        return try {
            android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjectMeta — dirName=$dirName")
            val projectDir = java.io.File(fileOperations.getWorkspaceRoot(), dirName)
            val metaFile = java.io.File(projectDir, ".agent/project.json")
            if (metaFile.exists()) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val project = json.decodeFromString(Project.serializer(), metaFile.readText())
                android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjectMeta — found meta file for '$dirName', id=${project.id}")
                project
            } else {
                android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjectMeta — no meta file for '$dirName'")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("NuclearBoy", "[ProjectVM] loadProjectMeta FAILED — ${e.message}")
            null
        }
    }
}
