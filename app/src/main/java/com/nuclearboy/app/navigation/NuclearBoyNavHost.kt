package com.nuclearboy.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nuclearboy.app.ui.projects.ProjectViewModel
import com.nuclearboy.app.ui.splash.SplashScreen
import com.nuclearboy.app.ui.tutorial.TutorialScreen
import com.nuclearboy.ui.chat.ChatScreen

object NavRoutes {
    const val SPLASH = "splash"
    const val PROJECT_LIST = "project_list"
    const val CHAT = "chat/{projectId}?initialMessage={initialMessage}"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val TUTORIAL = "tutorial"
    const val SKILL_MANAGER = "skill_manager"

    fun chatRoute(projectId: String, initialMessage: String = "") =
        "chat/$projectId" + if (initialMessage.isNotEmpty()) "?initialMessage=$initialMessage" else ""
}

@Composable
fun NuclearBoyNavHost(
    navController: NavHostController,
    projectViewModel: ProjectViewModel,
    onMenuClick: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH,
        enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 } },
        exitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 4 } },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 } },
    ) {
        // ── Splash ────────────────────────────────────────
        composable(NavRoutes.SPLASH) {
            android.util.Log.e("NuclearBoy", "[NavHost] route=SPLASH")
            SplashScreen(
                onComplete = {
                    navController.navigate(NavRoutes.chatRoute("__general__")) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        // ── Chat (General Agent + 项目对话) ──────────────
        composable(
            route = NavRoutes.CHAT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("initialMessage") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val initialMessage = backStackEntry.arguments?.getString("initialMessage") ?: ""
            android.util.Log.e("NuclearBoy", "[NavHost] route=CHAT projectId=$projectId")
            val ctx = androidx.compose.ui.platform.LocalContext.current

            ChatScreen(
                projectId = projectId,
                initialMessage = initialMessage,
                onNavigateBack = { navController.popBackStack() },
                onMenuClick = onMenuClick,
                onNotification = { msg, project ->
                    android.util.Log.e("NuclearBoy", "[NavHost] notification msg='$msg' project=$project")
                    when (msg) {
                        "thinking" -> com.nuclearboy.app.service.AgentForegroundService.start(ctx, project)
                        "stop" -> com.nuclearboy.app.service.AgentForegroundService.stop(ctx)
                        else -> com.nuclearboy.app.service.AgentForegroundService.update(ctx, msg, project)
                    }
                },
            )
        }

        composable(NavRoutes.SETTINGS) {
            android.util.Log.e("NuclearBoy", "[NavHost] route=SETTINGS")
            com.nuclearboy.app.ui.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onMenuClick = onMenuClick,
                onNavigateToTutorial = { navController.navigate(NavRoutes.TUTORIAL) },
            )
        }

        // ── API Key Tutorial ──────────────────────────────
        composable(NavRoutes.TUTORIAL) {
            android.util.Log.e("NuclearBoy", "[NavHost] route=TUTORIAL")
            TutorialScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.SKILL_MANAGER) {
            android.util.Log.e("NuclearBoy", "[NavHost] route=SKILL_MANAGER")
            com.nuclearboy.app.ui.skills.SkillManagerPanel(
                skillManager = projectViewModel.skillManager,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.ONBOARDING) {
            android.util.Log.e("NuclearBoy", "[NavHost] route=ONBOARDING")
            val settingsViewModel: com.nuclearboy.app.ui.settings.SettingsViewModel = hiltViewModel()
            com.nuclearboy.app.ui.onboarding.OnboardingScreen(
                apiKeyManager = settingsViewModel.apiKeyManager,
                onComplete = {
                    navController.navigate(NavRoutes.chatRoute("__general__")) {
                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
    }
}
