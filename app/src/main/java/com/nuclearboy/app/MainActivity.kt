package com.nuclearboy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nuclearboy.app.navigation.NavRoutes
import com.nuclearboy.app.navigation.NuclearBoyNavHost
import com.nuclearboy.app.ui.projects.ProjectViewModel
import com.nuclearboy.app.ui.sidebar.SidebarContent
import com.nuclearboy.app.ui.theme.NuclearBoyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("NuclearBoy", "[MainActivity] onCreate")

        // Check API key status
        val apiKeyManager = com.nuclearboy.api.deepseek.ApiKeyManager(this)
        val keyStatus = if (apiKeyManager.getActiveKey() != null) "configured" else "missing"
        android.util.Log.e("NuclearBoy", "[MainActivity] API key status: $keyStatus")

        enableEdgeToEdge()
        setContent { NuclearBoyTheme(darkTheme = true) { NuclearBoyMainScreen() } }
    }
}

@Composable
private fun NuclearBoyMainScreen() {
    val navController = rememberNavController()
    val projectViewModel: ProjectViewModel = hiltViewModel()
    val projects by projectViewModel.projects.collectAsState()
    val activeSkills by projectViewModel.activeSkills.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Track current route
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val currentProjectId = currentEntry?.arguments?.getString("projectId")

    // Log drawer state changes
    LaunchedEffect(drawerState.isOpen) {
        android.util.Log.e("NuclearBoy", "[MainActivity] Drawer ${if (drawerState.isOpen) "OPENED" else "CLOSED"}")
    }

    // Log navigation events
    LaunchedEffect(currentRoute, currentProjectId) {
        android.util.Log.e("NuclearBoy", "[MainActivity] Navigation — route=$currentRoute, projectId=$currentProjectId")
    }

    // Back handler: close drawer first, then normal back
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateToProject(projectId: String) {
        scope.launch { drawerState.close() }
        projectViewModel.selectProject(projectId)
        navController.navigate(NavRoutes.chatRoute(projectId)) {
            popUpTo(NavRoutes.PROJECT_LIST) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun createAndNavigate(name: String) {
        scope.launch { drawerState.close() }
        projectViewModel.createProject(name)
        navController.navigate(NavRoutes.chatRoute(name)) {
            popUpTo(NavRoutes.PROJECT_LIST) { inclusive = false }
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            SidebarContent(
                projects = projects,
                currentProjectId = currentProjectId,
                activeSkills = activeSkills,
                onProjectSelected = { navigateToProject(it) },
                onCreateProject = { createAndNavigate(it) },
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            NuclearBoyNavHost(
                navController = navController,
                projectViewModel = projectViewModel,
                onMenuClick = { scope.launch { drawerState.open() } },
            )
        }
    }
}
