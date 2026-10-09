package com.agent1.android.productivity.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
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

    /** path 为 URL 编码后的 workspace 相对路径（含 `/`），Navigation 会自动解码。 */
    const val PREVIEW = "preview/{sessionId}/{path}"
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
                onOpenHtmlPreview = { sessionId, relativePath ->
                    nav.navigate("preview/${Uri.encode(sessionId)}/${Uri.encode(relativePath)}")
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
        composable(
            route = Routes.PREVIEW,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val relativePath = entry.arguments?.getString("path").orEmpty()
            HtmlPreviewScreen(
                sessionId = sessionId,
                relativePath = relativePath,
                onBack = { nav.popBackStack() },
                onSendFeedback = { draft ->
                    // 不退出预览页：直接复用 HOME 的 ViewModelStore 里同一 ChatViewModel
                    // 实例（同 key），发送后留在预览页等文件热更新。
                    sendToHomeChat(nav, Routes.HOME, appContext, sessionId, draft)
                },
            )
        }
    }
}

private fun sendToHomeChat(
    nav: androidx.navigation.NavController,
    homeRoute: String,
    appContext: android.content.Context,
    sessionId: String,
    draft: String,
): Boolean {
    return runCatching {
        // 预览只从聊天页 push，HOME 必在栈中；复用其 ViewModelStore 中的
        // ChatViewModel（同 key），与聊天页共享同一会话状态与 run 队列。
        val homeEntry = nav.getBackStackEntry(homeRoute)
        val provider = androidx.lifecycle.ViewModelProvider(
            homeEntry.viewModelStore,
            simpleFactory { com.agent1.android.productivity.ui.viewmodel.ChatViewModel(appContext, sessionId, "") },
        )
        val vm = provider.get("chat-$sessionId", com.agent1.android.productivity.ui.viewmodel.ChatViewModel::class.java)
        vm.sendMessage(draft)
    }.getOrDefault(false)
}

private fun <T : androidx.lifecycle.ViewModel> simpleFactory(
    create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
