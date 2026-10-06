package com.agent1.android.productivity.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agent1.android.productivity.logic.business.McpSettingsCoordinator
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import com.agent1.android.productivity.ui.viewmodel.CapabilitySearchViewModel
import com.agent1.android.productivity.ui.viewmodel.McpSettingsViewModel
import com.agent1.android.productivity.ui.viewmodel.ModelSettingsViewModel
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel
import com.agent1.android.productivity.ui.viewmodel.SystemPromptViewModel

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "model-settings"
    const val MCP = "mcp-settings"
    const val CAPABILITIES = "capabilities"
    const val PROMPT = "system-prompt/{sessionId}"
}

@Composable
fun ProductivityNavHost() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: SessionListViewModel = viewModel(
                factory = simpleFactory { SessionListViewModel(appContext) },
            )
            ProductivityHome(
                sessionListViewModel = vm,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenMcp = { nav.navigate(Routes.MCP) },
                onOpenCapabilities = { nav.navigate(Routes.CAPABILITIES) },
                onOpenSystemPrompt = { sessionId ->
                    val id = sessionId.ifBlank { "-" }
                    nav.navigate("system-prompt/$id")
                },
            )
        }
        composable(Routes.SETTINGS) {
            val coordinator = remember(appContext) { ModelSettingsCoordinator(appContext) }
            val vm: ModelSettingsViewModel = viewModel(
                factory = simpleFactory { ModelSettingsViewModel(coordinator) },
            )
            ModelSettingsScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.MCP) {
            val coordinator = remember(appContext) { McpSettingsCoordinator(appContext) }
            val modelSettings = remember(appContext) { ModelSettingsCoordinator(appContext) }
            val vm: McpSettingsViewModel = viewModel(
                factory = simpleFactory { McpSettingsViewModel(coordinator, modelSettings) },
            )
            McpSettingsScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.CAPABILITIES) {
            val vm: CapabilitySearchViewModel = viewModel(
                factory = simpleFactory { CapabilitySearchViewModel(appContext) },
            )
            CapabilitySearchScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.PROMPT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val raw = entry.arguments?.getString("sessionId").orEmpty()
            val sessionId = if (raw == "-") "" else raw
            val vm: SystemPromptViewModel = viewModel(
                key = "prompt-$sessionId",
                factory = simpleFactory { SystemPromptViewModel(appContext, sessionId) },
            )
            SystemPromptScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

private fun <T : androidx.lifecycle.ViewModel> simpleFactory(
    create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
