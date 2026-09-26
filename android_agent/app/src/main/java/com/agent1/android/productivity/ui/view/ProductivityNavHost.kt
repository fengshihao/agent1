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
import android.net.Uri
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.ModelSettingsViewModel
import com.agent1.android.productivity.ui.viewmodel.SessionListViewModel

private object Routes {
    const val LIST = "sessions"
    const val SETTINGS = "model-settings"
    const val CHAT = "chat/{sessionId}?title={title}"
}

private fun chatRoute(sessionId: String, title: String): String {
    val encoded = Uri.encode(title)
    return "chat/$sessionId?title=$encoded"
}

@Composable
fun ProductivityNavHost() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            val vm: SessionListViewModel = viewModel(
                factory = simpleFactory { SessionListViewModel(appContext) },
            )
            SessionListScreen(
                viewModel = vm,
                onOpenSession = { meta ->
                    nav.navigate(chatRoute(meta.sessionId, meta.title.ifBlank { "新对话" }))
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
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
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "新对话"
                },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty().ifBlank { "新对话" }
            val vm: ChatViewModel = viewModel(
                factory = simpleFactory { ChatViewModel(appContext, sessionId, title) },
            )
            ChatScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
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
