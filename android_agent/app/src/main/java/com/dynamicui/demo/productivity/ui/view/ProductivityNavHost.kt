package com.dynamicui.demo.productivity.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dynamicui.demo.productivity.logic.business.ProductivityGatewayProvider
import com.dynamicui.demo.productivity.ui.viewmodel.ChatViewModel
import com.dynamicui.demo.productivity.ui.viewmodel.SessionListViewModel

private object Routes {
    const val LIST = "sessions"
    const val CHAT = "chat/{sessionId}"
}

@Composable
fun ProductivityNavHost() {
    val context = LocalContext.current
    val gateway = ProductivityGatewayProvider.get(context)
    val nav = rememberNavController()

    ProductivityTheme {
    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            val vm: SessionListViewModel = viewModel(
                factory = simpleFactory { SessionListViewModel(gateway) },
            )
            SessionListScreen(
                viewModel = vm,
                onOpenSession = { meta ->
                    nav.navigate("chat/${meta.sessionId}")
                },
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val title = gateway.listSessions().firstOrNull { it.sessionId == sessionId }?.title
                ?: "新对话"
            val vm: ChatViewModel = viewModel(
                factory = simpleFactory { ChatViewModel(gateway, sessionId, title) },
            )
            ChatScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
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
