package com.agent1.android.productivity.ui.view

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.agent1.android.productivity.logic.business.AgentAppLinks
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
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
    const val ABOUT = "about"
    const val PROMPT = "system-prompt/{sessionId}"
    /** sessionId 为 "-" 表示无活动会话（落位页进入，禁用「插入会话」）。 */
    const val ARTIFACTS = "artifact-library/{sessionId}"

    /** path 为 URL 编码后的 workspace 相对路径（含 `/`），Navigation 会自动解码。 */
    const val PREVIEW = "preview/{sessionId}/{path}"
}

/** 盖在上面的那一层淡入淡出。底下的聊天页保持不透明，避免两页内容叠在一起。 */
private const val NAV_FADE_MILLIS = 300

@Composable
fun ProductivityNavHost() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val nav = rememberNavController()
    val activity = context as? ComponentActivity
    var incomingApiKey by remember { mutableStateOf<String?>(null) }
    fun takeIncomingKey(intent: Intent?) {
        val key = AgentAppLinks.apiKeyFrom(intent?.data) ?: return
        incomingApiKey = key
        intent?.data = Uri.parse(AgentAppLinks.MODEL_SETTINGS)
    }
    DisposableEffect(nav, activity) {
        if (activity == null) {
            return@DisposableEffect onDispose { }
        }
        takeIncomingKey(activity.intent)
        val listener = Consumer<Intent> { incoming ->
            takeIncomingKey(incoming)
            nav.handleDeepLink(incoming)
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    val sessionListViewModel: SessionListViewModel = viewModel(
        factory = simpleFactory { SessionListViewModel(appContext) },
    )
    // 聊天层不读 back stack，避免打开产物库/设置时整页重绘、消息列表跟着闪。
    Box(Modifier.fillMaxSize()) {
        PersistentChatLayer(
            sessionListViewModel = sessionListViewModel,
            nav = nav,
        )
        ProductivityOverlayNav(
            nav = nav,
            activity = activity,
            appContext = appContext,
            incomingApiKey = incomingApiKey,
            onIncomingApiKeyConsumed = { incomingApiKey = null },
        )
    }
}

@Composable
private fun PersistentChatLayer(
    sessionListViewModel: SessionListViewModel,
    nav: NavHostController,
) {
    val openSettings = remember(nav) { { nav.navigate(Routes.SETTINGS) } }
    val openMcp = remember(nav) { { nav.navigate(Routes.MCP) } }
    val openCapabilities = remember(nav) { { nav.navigate(Routes.CAPABILITIES) } }
    val openAbout = remember(nav) { { nav.navigate(Routes.ABOUT) } }
    val openSystemPrompt = remember(nav) {
        { sessionId: String ->
            val id = sessionId.ifBlank { "-" }
            nav.navigate("system-prompt/$id")
        }
    }
    val openHtmlPreview = remember(nav) {
        { sessionId: String, relativePath: String ->
            nav.navigate("preview/${Uri.encode(sessionId)}/${Uri.encode(relativePath)}")
        }
    }
    val openArtifacts = remember(nav) {
        { sessionId: String ->
            val id = sessionId.ifBlank { "-" }
            nav.navigate("artifact-library/${Uri.encode(id)}")
        }
    }
    ProductivityHome(
        sessionListViewModel = sessionListViewModel,
        onOpenSettings = openSettings,
        onOpenMcp = openMcp,
        onOpenCapabilities = openCapabilities,
        onOpenSystemPrompt = openSystemPrompt,
        onOpenHtmlPreview = openHtmlPreview,
        onOpenArtifacts = openArtifacts,
        onOpenAbout = openAbout,
    )
}

@Composable
private fun ProductivityOverlayNav(
    nav: NavHostController,
    activity: ComponentActivity?,
    appContext: android.content.Context,
    incomingApiKey: String?,
    onIncomingApiKeyConsumed: () -> Unit,
) {
    val navEntry by nav.currentBackStackEntryAsState()
    val overlayVisible = navEntry?.destination?.route?.let { it != Routes.HOME } == true
    val navModifier = if (overlayVisible) {
        Modifier.fillMaxSize()
    } else {
        // 在 home 占位层上透传点击，否则挡在聊天页上面点不到。
        Modifier
            .fillMaxSize()
            .pointerInteropFilter { false }
    }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = navModifier,
        contentAlignment = Alignment.TopStart,
        enterTransition = { fadeIn(animationSpec = tween(NAV_FADE_MILLIS)) },
        exitTransition = { fadeOut(animationSpec = tween(NAV_FADE_MILLIS), targetAlpha = 1f) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_FADE_MILLIS), initialAlpha = 1f) },
        popExitTransition = { fadeOut(animationSpec = tween(NAV_FADE_MILLIS)) },
        sizeTransform = { SizeTransform(clip = false) { _, _ -> snap() } },
    ) {
        composable(Routes.HOME) {
            Box(Modifier.fillMaxSize())
        }
        composable(
            route = Routes.SETTINGS,
            deepLinks = listOf(navDeepLink { uriPattern = AgentAppLinks.MODEL_SETTINGS }),
        ) {
            NavPage {
                val coordinator = remember(appContext) { ModelSettingsCoordinator(appContext) }
                val vm: ModelSettingsViewModel = viewModel(
                    factory = simpleFactory { ModelSettingsViewModel(coordinator) },
                )
                LaunchedEffect(incomingApiKey) {
                    val key = incomingApiKey ?: return@LaunchedEffect
                    onIncomingApiKeyConsumed()
                    vm.importIncomingKey(key)
                }
                ModelSettingsScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(Routes.MCP) {
            NavPage {
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
        }
        composable(Routes.ABOUT) {
            NavPage { AboutScreen(onBack = { nav.popBackStack() }) }
        }
        composable(Routes.CAPABILITIES) {
            NavPage {
                val vm: CapabilitySearchViewModel = viewModel(
                    factory = simpleFactory { CapabilitySearchViewModel(appContext) },
                )
                CapabilitySearchScreen(
                    viewModel = vm,
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(
            route = Routes.ARTIFACTS,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            NavPage {
                val raw = entry.arguments?.getString("sessionId").orEmpty()
                val sessionId = if (raw == "-") "" else raw
                val vm: ArtifactLibraryViewModel = viewModel(
                    key = "artifacts-$sessionId",
                    factory = simpleFactory { ArtifactLibraryViewModel(appContext, sessionId) },
                )
                val chatVm = remember(sessionId) {
                    if (sessionId.isBlank()) {
                        null
                    } else {
                        resolveHomeChatViewModel(activity, appContext, sessionId)
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
        }
        composable(
            route = Routes.PROMPT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            NavPage {
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
        composable(
            route = Routes.PREVIEW,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { entry ->
            NavPage {
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                val relativePath = entry.arguments?.getString("path").orEmpty()
                val chatVm = remember(sessionId) {
                    resolveHomeChatViewModel(activity, appContext, sessionId)
                }
                if (WorkspaceFileActions.isDocxPreviewableInApp(relativePath)) {
                    DocxPreviewScreen(
                        sessionId = sessionId,
                        relativePath = relativePath,
                        onBack = { nav.popBackStack() },
                        onSendFeedback = { draft -> chatVm?.sendMessage(draft) == true },
                    )
                } else {
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
    }
}

/** 每个目的地铺满整屏并带上不透明底色，避免淡入时透出底下的聊天页。点击落在空白处，不传到下层聊天页。 */
@Composable
private fun NavPage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        content()
    }
}

/** 与一直留在屏幕上的聊天页共用 Activity 里同 key 的 ChatViewModel。 */
private fun resolveHomeChatViewModel(
    activity: ComponentActivity?,
    appContext: android.content.Context,
    sessionId: String,
): com.agent1.android.productivity.ui.viewmodel.ChatViewModel? {
    if (activity == null || sessionId.isBlank()) return null
    return runCatching {
        val provider = androidx.lifecycle.ViewModelProvider(
            activity,
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
