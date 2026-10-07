package com.agent1.android.productivity.ui.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import androidx.compose.ui.unit.dp

/** 单个会话的聊天屏：顶栏 + 消息列表 + ask-user 表单 + 输入条。 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    title: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSystemPrompt: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var input by rememberSaveable { mutableStateOf("") }
    var moreMenu by rememberSaveable { mutableStateOf(false) }
    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.onUserPickedFiles(uris)
    }
    val launchPickFiles = {
        pickFilesLauncher.launch(arrayOf("*/*"))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshConfigSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    AgentTopBar(
                        title = title.ifBlank { state.title }.ifBlank { "新对话" },
                        subtitle = null,
                        leading = {
                            TopBarIconButton(
                                icon = Icons.Filled.Menu,
                                contentDescription = "会话列表",
                                onClick = onOpenDrawer,
                            )
                        },
                        actions = {
                            ChatMoreMenu(
                                expanded = moreMenu,
                                exportInProgress = state.exportInProgress,
                                onOpen = { moreMenu = true },
                                onDismiss = { moreMenu = false },
                                onOpenSettings = onOpenSettings,
                                onOpenMcp = onOpenMcp,
                                onOpenCapabilities = onOpenCapabilities,
                                onOpenSystemPrompt = onOpenSystemPrompt,
                                onBrief = { viewModel.exportBriefTranscript(context) },
                                onExportDiagnostics = { viewModel.exportDiagnostics(context) },
                            )
                            TopBarIconButton(
                                icon = Icons.Filled.Add,
                                contentDescription = "新建对话",
                                onClick = onNewChat,
                            )
                        },
                    )
                    AgentHairline()
                    if (!state.exportMessage.isNullOrBlank()) {
                        Text(
                            state.exportMessage.orEmpty(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.configError != null) {
                        ConfigErrorBanner(state.configError.orEmpty())
                    }
                    if (!state.transcriptLoadError.isNullOrBlank()) {
                        ConfigErrorBanner("对话加载失败：${state.transcriptLoadError}")
                    }
                    if (!state.fileImportMessage.isNullOrBlank()) {
                        Text(
                            state.fileImportMessage.orEmpty(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            bottomBar = {
                // 非 edge-to-edge（decorFitsSystemWindows）下由 adjustResize 抬升窗口；勿再叠 imePadding。
                Column {
                    if (state.accessibleFilePaths.isNotEmpty()) {
                        AccessibleFilesStrip(paths = state.accessibleFilePaths)
                        AgentHairline()
                    }
                    ChatComposer(
                        value = input,
                        onValueChange = { input = it },
                        isRunning = state.isRunning,
                        canSend = state.configError == null && input.isNotBlank() && !state.isRunning,
                        enabled = state.configError == null,
                        onPickFiles = launchPickFiles,
                        pickFilesEnabled = state.configError == null && !state.isRunning,
                        onSend = {
                            viewModel.sendMessage(input)
                            input = ""
                        },
                        onStop = { viewModel.stopRun() },
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ChatMessageList(
                    state = state,
                    onPickFiles = launchPickFiles,
                    pickFilesEnabled = state.configError == null && !state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                state.pendingAskUser?.let { form ->
                    AskUserFormPanel(
                        form = form,
                        enabled = state.configError == null && !state.isRunning,
                        validationError = form.validationError,
                        onTextChange = viewModel::onAskUserTextChange,
                        onSingleSelect = viewModel::onAskUserSingleSelect,
                        onMultiToggle = viewModel::onAskUserMultiToggle,
                        onSubmit = viewModel::submitAskUserForm,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibleFilesStrip(paths: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "已选文件（本会话）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            paths.joinToString(" · "),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    canSend: Boolean,
    enabled: Boolean,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val fieldShape = RoundedCornerShape(22.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        AgentHairline()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPickFiles,
                    enabled = pickFilesEnabled,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        AgentIcons.AttachFile,
                        contentDescription = "选择文件",
                        modifier = Modifier.size(22.dp),
                        tint = if (pickFilesEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "输入消息…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    enabled = enabled,
                    shape = fieldShape,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                FilledIconButton(
                    onClick = { if (isRunning) onStop() else onSend() },
                    enabled = isRunning || canSend,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    ),
                ) {
                    Icon(
                        if (isRunning) AgentIcons.Stop else Icons.Filled.Send,
                        contentDescription = if (isRunning) "停止" else "发送",
                        modifier = Modifier.size(if (isRunning) 16.dp else 20.dp),
                    )
                }
            }
        }
    }
}
