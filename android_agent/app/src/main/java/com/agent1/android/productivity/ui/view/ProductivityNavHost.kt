package com.agent1.android.productivity.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agent1.android.productivity.logic.business.McpSettingsCoordinator
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import com.agent1.android.productivity.ui.viewmodel.McpSettingsViewModel
import com.agent1.android.productivity.ui.viewmodel.ModelSettingsViewModel
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "model-settings"
    const val MCP = "mcp-settings"
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
            val vm: McpSettingsViewModel = viewModel(
                factory = simpleFactory { McpSettingsViewModel(coordinator) },
            )
            McpSettingsScreen(
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
