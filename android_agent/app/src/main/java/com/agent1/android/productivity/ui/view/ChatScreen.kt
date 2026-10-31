package com.agent1.android.productivity.ui.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.agent1.android.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers
import com.agent1.android.productivity.logic.business.AttachmentComposerLabel
import com.agent1.android.productivity.logic.business.AttachmentSheetDraft
import com.agent1.android.productivity.ui.viewmodel.ChatViewModel
import com.agent1.android.productivity.ui.viewmodel.isFreshSession
import com.agent1.javaagent.modelcatalog.RuntimeConfigSummary
import androidx.compose.ui.unit.Dp
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
    /** 打开产物库（顶栏文件夹，位于「更多」左侧）。 */
    onOpenArtifacts: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenHostCaps: () -> Unit = {},
    /** 传入且文件可内置预览（html）时，点「打开」走 app 内预览路由。 */
    onOpenInApp: ((String) -> Unit)? = null,
    /** 当前会话是否还在加载、是否还没有任何对话。供「新对话」决定要不要再开一场。 */
    onSessionFreshness: (loading: Boolean, fresh: Boolean) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    KeepScreenOnWhile(
        active = state.isRunning || state.attachmentImportInProgress,
    )
    val loadingTranscript = state.isLoadingTranscript
    val freshSession = state.isFreshSession()
    LaunchedEffect(loadingTranscript, freshSession) {
        onSessionFreshness(loadingTranscript, freshSession)
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var input by rememberSaveable { mutableStateOf("") }
    var moreMenu by rememberSaveable { mutableStateOf(false) }
    var attachmentSheetVisible by rememberSaveable { mutableStateOf(false) }
    var traceSheetVisible by rememberSaveable { mutableStateOf(false) }
    var attachmentAssistantHint by remember { mutableStateOf<UserFileRequestMarkers.Hint?>(null) }
    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPickedFilesFromDevice(uris)
        }
    }
    val needsUserKeySetup = state.needsUserKeySetup
    val composerBlocked = state.configError != null && !needsUserKeySetup
    val attachmentsEnabled = !composerBlocked && !state.isRunning && state.pendingAskUser == null
    val canPickMoreAttachments = attachmentsEnabled &&
        viewModel.attachmentSlotsRemaining(state) > 0
    val openFileList: (UserFileRequestMarkers.Hint?) -> Unit = { hint ->
        attachmentAssistantHint = hint
        attachmentSheetVisible = true
    }
    val launchPickFromDevice = {
        pickFilesLauncher.launch(arrayOf("*/*"))
    }
    val showComposerFileList = state.accessibleFileEntries.isNotEmpty() ||
        state.pendingAttachmentImports.isNotEmpty() ||
        state.pendingAttachmentRemovals.isNotEmpty()

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
                        title = title.ifBlank { state.title }.ifBlank { stringResource(R.string.chat_new_session) },
                        subtitle = null,
                        leading = {
                            TopBarIconButton(
                                icon = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_session_list),
                                onClick = onOpenDrawer,
                            )
                        },
                        actions = {
                            TopBarIconButton(
                                icon = AgentIcons.Folder,
                                contentDescription = stringResource(R.string.cd_artifact_library),
                                onClick = onOpenArtifacts,
                            )
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
                                onOpenToolTrace = { traceSheetVisible = true },
                                onOpenAbout = onOpenAbout,
                                onOpenHostCaps = onOpenHostCaps,
                            )
                        },
                    )
                    if (!state.exportMessage.isNullOrBlank()) {
                        Text(
                            state.exportMessage.orEmpty(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.configError != null && !state.needsUserKeySetup) {
                        ConfigErrorBanner(state.configError.orEmpty())
                    }
                    if (!state.transcriptLoadError.isNullOrBlank()) {
                        ConfigErrorBanner(
                            stringResource(R.string.chat_load_failed, state.transcriptLoadError.orEmpty()),
                        )
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
        ) { innerPadding ->
            // 非 edge-to-edge（decorFitsSystemWindows）下由 adjustResize 抬升窗口；勿再叠 imePadding。
            // 输入条浮在列表上，上沿渐变透出对话，而不是一条硬边裁切。
            val density = LocalDensity.current
            var composerHeight by remember { mutableStateOf(0.dp) }
            val fade = 36.dp
            val pendingAsk = state.pendingAskUser
            val overlap = if (pendingAsk != null) 0.dp else fade
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val askCardHeight = minOf(312.dp, maxHeight)
                val attachmentUnsavedDraft = AttachmentComposerLabel.hasUnsavedDraft(
                    state.pendingAttachmentImports,
                    state.pendingAttachmentRemovals,
                )
                val contentBottom = (composerHeight - overlap).coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentBottom),
                ) {
                    ChatMessageList(
                        state = state,
                        onOpenAttachments = openFileList,
                        onNewChat = onNewChat,
                        attachmentsActionEnabled = !composerBlocked && !state.isRunning,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onOpenInApp = onOpenInApp,
                        onOpenSettings = onOpenSettings,
                        topContentPadding = 16.dp,
                        onSetupKeyDraftChange = viewModel::onSetupKeyDraftChange,
                        onSubmitSetupKey = viewModel::applySetupKey,
                        onOpenTrace = { traceSheetVisible = true },
                    )
                    pendingAsk?.let { form ->
                        AskUserFormPanel(
                            form = form,
                            workspaceAbsolutePath = state.workspacePath,
                            enabled = !composerBlocked && !state.isRunning,
                            validationError = form.validationError,
                            onTextChange = viewModel::onAskUserTextChange,
                            onSingleSelect = viewModel::onAskUserSingleSelect,
                            onMultiToggle = viewModel::onAskUserMultiToggle,
                            onWorkspaceFileToggle = viewModel::onAskUserWorkspaceFileToggle,
                            onWorkspaceFileRemove = viewModel::onAskUserWorkspaceFileRemove,
                            onSubmit = viewModel::submitAskUserForm,
                            onCustomAnswerChange = viewModel::onAskUserCustomAnswerChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(askCardHeight),
                        )
                    }
                }
                ChatComposer(
                    value = input,
                    onValueChange = { input = it },
                    isRunning = state.isRunning,
                    canSend = !composerBlocked &&
                        !state.isRunning &&
                        pendingAsk == null &&
                        input.isNotBlank(),
                    enabled = !composerBlocked && pendingAsk == null,
                    inputPlaceholder = if (pendingAsk == null) {
                        stringResource(R.string.chat_input_hint)
                    } else {
                        stringResource(R.string.chat_input_answer_card)
                    },
                    fileListLabel = AttachmentComposerLabel.resolve(
                        state.pendingAttachmentImports,
                        state.accessibleFileEntries,
                        state.pendingAttachmentRemovals,
                    ),
                    showSelectedFilesBar = showComposerFileList,
                    hasAttachmentDraft = attachmentUnsavedDraft,
                    onPickAttachment = launchPickFromDevice,
                    onOpenFileList = { openFileList(null) },
                    attachmentsEnabled = attachmentsEnabled,
                    canPickMoreAttachments = canPickMoreAttachments,
                    modelLabel = composerModelLabel(state.configSummary),
                    onOpenModel = onOpenSettings,
                    showContinue = state.offerContinue && !state.isRunning && pendingAsk == null,
                    onContinue = viewModel::continueAfterBudget,
                    onSend = {
                        if (viewModel.sendMessage(input)) input = ""
                    },
                    onStop = { viewModel.stopRun() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { size ->
                            composerHeight = with(density) { size.height.toDp() }
                        },
                )
                AgentTopFade(Modifier.align(Alignment.TopCenter))
            }
        }
        val attachmentDraftRows = AttachmentSheetDraft.rows(
            state.accessibleFileEntries,
            state.pendingAttachmentImports,
            state.pendingAttachmentRemovals,
        )
        val sheetUnsavedDraft = AttachmentComposerLabel.hasUnsavedDraft(
            state.pendingAttachmentImports,
            state.pendingAttachmentRemovals,
        )
        SessionAttachmentsSheet(
            visible = attachmentSheetVisible,
            draftRows = attachmentDraftRows,
            hasUnsavedDraft = sheetUnsavedDraft,
            importInProgress = state.attachmentImportInProgress,
            assistantHint = attachmentAssistantHint,
            addEnabled = attachmentsEnabled,
            onDismiss = {
                viewModel.clearAttachmentSheetStaging()
                attachmentSheetVisible = false
                attachmentAssistantHint = null
            },
            onRemoveRow = viewModel::removeAttachmentSheetRow,
            onConfirm = {
                viewModel.commitAttachmentSheetChanges {
                    attachmentSheetVisible = false
                    attachmentAssistantHint = null
                }
            },
        )
        RunTraceSheet(
            visible = traceSheetVisible,
            state = state,
            onDismiss = { traceSheetVisible = false },
        )
    }
}

/**
 * 底部输入条。
 *
 * 底栏左侧：加号与文件入口；右侧为当前模型。
 */
@Composable
internal fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    canSend: Boolean,
    enabled: Boolean,
    inputPlaceholder: String,
    fileListLabel: AttachmentComposerLabel.Resolved = AttachmentComposerLabel.Resolved(
        singleFileName = null,
        selectedCount = 0,
    ),
    showSelectedFilesBar: Boolean = false,
    hasAttachmentDraft: Boolean = false,
    onPickAttachment: () -> Unit,
    onOpenFileList: () -> Unit,
    attachmentsEnabled: Boolean,
    canPickMoreAttachments: Boolean = attachmentsEnabled,
    modelLabel: String = "",
    onOpenModel: (() -> Unit)? = null,
    onSend: () -> Unit,
    onStop: () -> Unit,
    showContinue: Boolean = false,
    onContinue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bar = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to bar.copy(alpha = 0.35f),
                        1f to bar,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bar)
                .padding(bottom = 8.dp),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (showContinue) {
                        Text(
                            stringResource(R.string.chat_continue_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(stringResource(R.string.chat_continue))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    ComposerField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        placeholder = inputPlaceholder,
                        isRunning = isRunning,
                        canSend = canSend,
                        onSend = onSend,
                        onStop = onStop,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ComposerPickAttachmentButton(
                            enabled = canPickMoreAttachments,
                            onClick = onPickAttachment,
                        )
                        if (showSelectedFilesBar) {
                            ComposerSelectedFilesBar(
                                label = fileListLabel,
                                hasUnsavedDraft = hasAttachmentDraft,
                                enabled = attachmentsEnabled,
                                onClick = onOpenFileList,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (modelLabel.isNotBlank()) {
                            ComposerModelChip(label = modelLabel, onClick = onOpenModel)
                        }
                    }
                }
            }
        }
    }
}

/** 目录里有展示名就用展示名，否则用模型 id。 */
internal fun composerModelLabel(summary: RuntimeConfigSummary?): String {
    if (summary == null) return ""
    val named = summary.catalogMatch.orElse(null)?.displayName.orEmpty().trim()
    return named.ifBlank { summary.modelId.trim() }
}

@Composable
private fun ComposerModelChip(
    label: String,
    onClick: (() -> Unit)?,
) {
    val modelCd = stringResource(R.string.chat_model_cd, label)
    ComposerBarTappableRow(
        text = label,
        contentDescription = modelCd,
        onClick = onClick,
        maxWidth = 200.dp,
    )
}

@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    isRunning: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = MaterialTheme.colorScheme.onSurface
    val borderColor = when {
        focused && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        else -> ink.copy(alpha = 0.14f)
    }
    val textColor = if (enabled) ink else ink.copy(alpha = 0.38f)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(ink.copy(alpha = if (enabled) 0.07f else 0.04f))
            .border(1.dp, borderColor, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (canSend) onSend()
                },
            ),
            maxLines = 4,
            interactionSource = interaction,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (enabled) 0.75f else 0.4f,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        FilledIconButton(
            onClick = { if (isRunning) onStop() else onSend() },
            enabled = isRunning || canSend,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            ),
        ) {
            Icon(
                if (isRunning) AgentIcons.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isRunning) {
                    stringResource(R.string.action_stop)
                } else {
                    stringResource(R.string.action_send)
                },
                modifier = Modifier.size(if (isRunning) 14.dp else 18.dp),
            )
        }
    }
}
