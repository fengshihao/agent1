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
import com.agent1.android.productivity.ui.viewmodel.ArtifactLibraryViewModel
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
    /** sessionId 为 "-" 表示无活动会话（落位页进入，禁用「插入会话」）。 */
    const val ARTIFACTS = "artifact-library/{sessionId}"

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
                onOpenArtifacts = { sessionId ->
                    val id = sessionId.ifBlank { "-" }
                    nav.navigate("artifact-library/${Uri.encode(id)}")
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
            route = Routes.ARTIFACTS,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val raw = entry.arguments?.getString("sessionId").orEmpty()
            val sessionId = if (raw == "-") "" else raw
            val vm: ArtifactLibraryViewModel = viewModel(
                key = "artifacts-$sessionId",
                factory = simpleFactory { ArtifactLibraryViewModel(appContext, sessionId) },
            )
            // 与聊天页共享同一 ChatViewModel 实例（HOME 的 ViewModelStore，同 key）：
            // 插入产物后刷新聊天页 transcript 与「已选文件」条。
            val chatVm = remember(sessionId) {
                if (sessionId.isBlank()) {
                    null
                } else {
                    resolveHomeChatViewModel(nav, Routes.HOME, appContext, sessionId)
                }
            }
            ArtifactLibraryScreen(
                viewModel = vm,
                currentSessionId = sessionId,
                onBack = { nav.popBackStack() },
                onInserted = { chatVm?.reloadTranscript() },
                onOpenHtmlPreview = { previewSessionId, relativePath ->
                    nav.navigate(
                        "preview/${Uri.encode(previewSessionId)}/${Uri.encode(relativePath)}",
                    )
                },
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
            // 与聊天页共享同一 ChatViewModel 实例（HOME 的 ViewModelStore，同 key）：
            // 预览页直接发送反馈并实时展示 AI 运行状态。
            val chatVm = remember(sessionId) {
                resolveHomeChatViewModel(nav, Routes.HOME, appContext, sessionId)
            }
            HtmlPreviewScreen(
                sessionId = sessionId,
                relativePath = relativePath,
                onBack = { nav.popBackStack() },
                chatViewModel = chatVm,
                onSendFeedback = { draft -> chatVm?.sendMessage(draft) == true },
            )
        }
    }
}

/** 复用 HOME 的 ViewModelStore 中同 key 的 ChatViewModel；HOME 不在栈中（异常场景）返回 null。 */
private fun resolveHomeChatViewModel(
    nav: androidx.navigation.NavController,
    homeRoute: String,
    appContext: android.content.Context,
    sessionId: String,
): com.agent1.android.productivity.ui.viewmodel.ChatViewModel? {
    return runCatching {
        // 预览只从聊天页 push，HOME 必在栈中
        val homeEntry = nav.getBackStackEntry(homeRoute)
        val provider = androidx.lifecycle.ViewModelProvider(
            homeEntry.viewModelStore,
            simpleFactory { com.agent1.android.productivity.ui.viewmodel.ChatViewModel(appContext, sessionId, "") },
        )
        provider.get(
            "chat-$sessionId",
            com.agent1.android.productivity.ui.viewmodel.ChatViewModel::class.java,
        )
    }.getOrNull()
}

private fun <T : androidx.lifecycle.ViewModel> simpleFactory(
    create: () -> T,
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
