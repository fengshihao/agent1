package com.dynamicui.demo.pet.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynamicui.demo.R
import com.dynamicui.demo.agent.service.AgentForegroundService
import com.dynamicui.demo.pet.logic.business.voice.VoiceInputController
import com.dynamicui.demo.pet.logic.business.voice.VoiceInputSignal
import com.dynamicui.demo.pet.logic.business.voice.VoiceInputState
import com.dynamicui.demo.pet.logic.data.asr.DashScopeAsrTransport
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private class OverlayComposeHost : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    fun attach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}

private enum class PanelMode { Hidden, Minimized, Expanded }
private data class ChatBubble(val role: String, val content: String)

class PetOverlayManager(private val appContext: Context) {
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private val main = Handler(Looper.getMainLooper())
    private var petOffsetXPx: Int = 0
    private var petOffsetYPx: Int = 0
    private var panelExpandedForWindow: Boolean = false
    private val layoutType: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    private val overlayFlags: Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private fun paramsForExpanded() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        layoutType,
        overlayFlags,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun paramsForPetOnly() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        layoutType,
        overlayFlags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        x = petOffsetXPx
        y = petOffsetYPx
    }

    private fun applyWindowMode(expanded: Boolean) {
        panelExpandedForWindow = expanded
        val view = composeView ?: return
        val params = if (expanded) paramsForExpanded() else paramsForPetOnly()
        if (view.parent == null) wm.addView(view, params) else wm.updateViewLayout(view, params)
    }

    fun show(service: AgentForegroundService) {
        if (composeView != null) return
        val view = ComposeView(appContext)
        val host = OverlayComposeHost().apply { attach() }
        view.setViewTreeLifecycleOwner(host)
        view.setViewTreeSavedStateRegistryOwner(host)
        view.setContent {
            MaterialTheme {
                PetOverlayScreen(
                    service = service,
                    runOnMain = { block -> main.post { block() } },
                    onPanelModeChanged = { expanded -> main.post { applyWindowMode(expanded) } },
                    initialPetOffsetXPx = petOffsetXPx,
                    initialPetOffsetYPx = petOffsetYPx,
                    onPetOffsetChanged = { x, y ->
                        petOffsetXPx = x
                        petOffsetYPx = y
                        if (!panelExpandedForWindow) main.post { applyWindowMode(expanded = false) }
                    }
                )
            }
        }
        composeView = view
        applyWindowMode(expanded = false)
    }

    fun hide() { main.post { hideOnMainThread() } }
    private fun hideOnMainThread() {
        val v = composeView ?: return
        try {
            if (v.parent != null) {
                (v as? AbstractComposeView)?.disposeComposition()
                wm.removeView(v)
            }
        } catch (_: Exception) {
            try { if (v.parent != null) wm.removeViewImmediate(v) } catch (_: Exception) {}
        } finally {
            composeView = null
        }
    }
}

@Composable
private fun PetOverlayScreen(
    service: AgentForegroundService,
    runOnMain: (() -> Unit) -> Unit,
    onPanelModeChanged: (expanded: Boolean) -> Unit,
    initialPetOffsetXPx: Int,
    initialPetOffsetYPx: Int,
    onPetOffsetChanged: (x: Int, y: Int) -> Unit
) {
    var panelMode by remember { mutableStateOf(PanelMode.Hidden) }
    var phase by remember { mutableStateOf(AgentSessionPhase.Idle) }
    var endedByUserAbort by remember { mutableStateOf(false) }
    var liveAssistantMarkdown by remember { mutableStateOf("") }
    var panelManuallyHidden by remember { mutableStateOf(false) }
    var petOffsetX by remember { mutableFloatStateOf(initialPetOffsetXPx.toFloat()) }
    var petOffsetY by remember { mutableFloatStateOf(initialPetOffsetYPx.toFloat()) }
    var draftUserBubbleIndex by remember { mutableStateOf<Int?>(null) }
    val chatHistory = remember { mutableStateListOf<ChatBubble>() }
    val scope = rememberCoroutineScope()
    val transport = remember(scope) { DashScopeAsrTransport(scope, runOnMain) }
    val voiceController = remember(transport, service) { VoiceInputController(transport) { text -> service.submitUserMessage(text) } }
    var voiceState by remember { mutableStateOf(VoiceInputState.Idle) }
    val phaseRef = rememberUpdatedState(phase)

    LaunchedEffect(panelMode) { onPanelModeChanged(panelMode != PanelMode.Hidden) }
    LaunchedEffect(voiceController) {
        voiceController.signals.collectLatest { signal ->
            when (signal) {
                is VoiceInputSignal.StateChanged -> {
                    voiceState = signal.state
                    phase = when (signal.state) {
                        VoiceInputState.Idle -> if (phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming) phase else AgentSessionPhase.Idle
                        VoiceInputState.Pressing -> phase
                        VoiceInputState.Listening -> AgentSessionPhase.Listening
                        VoiceInputState.Transcribing -> AgentSessionPhase.Transcribing
                        VoiceInputState.Submitting -> AgentSessionPhase.Sending
                        VoiceInputState.Error -> AgentSessionPhase.Error
                    }
                }
                is VoiceInputSignal.PartialText -> {
                    val preview = signal.text.trim()
                    if (preview.isNotEmpty()) {
                        val content = "识别中：$preview"
                        val idx = draftUserBubbleIndex
                        if (idx != null && idx in 0 until chatHistory.size) chatHistory[idx] = chatHistory[idx].copy(content = content)
                        else {
                            chatHistory.add(ChatBubble(role = "user", content = content))
                            draftUserBubbleIndex = chatHistory.lastIndex
                        }
                    }
                }
                is VoiceInputSignal.FinalText -> {
                    val finalText = signal.text.trim()
                    if (finalText.isNotEmpty()) {
                        val idx = draftUserBubbleIndex
                        if (idx != null && idx in 0 until chatHistory.size) chatHistory[idx] = chatHistory[idx].copy(content = finalText)
                        else chatHistory.add(ChatBubble(role = "user", content = finalText))
                    }
                }
                is VoiceInputSignal.Submitted -> {
                    val submitted = signal.text.trim()
                    phase = AgentSessionPhase.Sending
                    panelManuallyHidden = false
                    val idx = draftUserBubbleIndex
                    if (idx != null && idx in 0 until chatHistory.size) chatHistory[idx] = chatHistory[idx].copy(content = submitted)
                    else chatHistory.add(ChatBubble(role = "user", content = submitted))
                    draftUserBubbleIndex = null
                    liveAssistantMarkdown = ""
                    chatHistory.add(ChatBubble(role = "assistant", content = ""))
                }
                is VoiceInputSignal.Error -> { phase = AgentSessionPhase.Error; draftUserBubbleIndex = null }
                is VoiceInputSignal.Cancelled -> { phase = AgentSessionPhase.Idle; draftUserBubbleIndex = null }
                VoiceInputSignal.Busy -> {}
            }
        }
    }

    DisposableEffect(service) {
        val listener = object : AgentForegroundService.AgentUiListener {
            override fun onAssistantStreaming(fullMarkdown: String) {
                liveAssistantMarkdown = fullMarkdown
                if (phaseRef.value == AgentSessionPhase.Sending && fullMarkdown.isNotBlank()) phase = AgentSessionPhase.Streaming
                if (chatHistory.isNotEmpty() && chatHistory.last().role == "assistant") chatHistory[chatHistory.lastIndex] = chatHistory.last().copy(content = fullMarkdown)
                else chatHistory.add(ChatBubble(role = "assistant", content = fullMarkdown))
                if ((phaseRef.value == AgentSessionPhase.Streaming || phaseRef.value == AgentSessionPhase.Sending) && !panelManuallyHidden) panelMode = PanelMode.Expanded
            }
            override fun onAgentFinished(finalMarkdown: String) {
                liveAssistantMarkdown = finalMarkdown
                if (chatHistory.isNotEmpty() && chatHistory.last().role == "assistant") chatHistory[chatHistory.lastIndex] = chatHistory.last().copy(content = finalMarkdown)
                else chatHistory.add(ChatBubble(role = "assistant", content = finalMarkdown))
                phase = AgentSessionPhase.Done
                endedByUserAbort = false
                if (!panelManuallyHidden) panelMode = PanelMode.Expanded
            }
            override fun onAgentAborted(partialMarkdown: String) {
                liveAssistantMarkdown = partialMarkdown
                if (chatHistory.isNotEmpty() && chatHistory.last().role == "assistant") chatHistory[chatHistory.lastIndex] = chatHistory.last().copy(content = partialMarkdown)
                else chatHistory.add(ChatBubble(role = "assistant", content = partialMarkdown))
                phase = AgentSessionPhase.Done
                endedByUserAbort = true
                if (!panelManuallyHidden) panelMode = PanelMode.Expanded
            }
            override fun onAgentError(message: String) {
                phase = AgentSessionPhase.Error
                endedByUserAbort = false
                if (!panelManuallyHidden) panelMode = PanelMode.Expanded
                liveAssistantMarkdown = "**错误**\n\n$message"
                chatHistory.add(ChatBubble(role = "assistant", content = liveAssistantMarkdown))
            }
        }
        service.addUiListener(listener)
        onDispose {
            voiceController.stopAll()
            service.removeUiListener(listener)
        }
    }

    val density = LocalDensity.current
    val cancelThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val petSizePx = with(density) { 44.dp.toPx() }
    val maxPetOffsetX = (screenWidthPx - petSizePx).coerceAtLeast(0f)
    val maxPetOffsetY = (screenHeightPx - petSizePx).coerceAtLeast(0f)
    val chatListState = rememberLazyListState()

    LaunchedEffect(chatHistory.size, chatHistory.lastOrNull()?.content) {
        if (chatHistory.isNotEmpty()) chatListState.animateScrollToItem(chatHistory.lastIndex)
    }

    @Composable
    fun PetControlsColumn() {
        var fingerOnPet by remember { mutableStateOf(false) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var upward by remember { mutableFloatStateOf(0f) }
            var swipeCancelled by remember { mutableStateOf(false) }
            val petListening = voiceState == VoiceInputState.Listening || voiceState == VoiceInputState.Transcribing
            val waitingServer = phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming
            val petFillColor = when {
                petListening -> Color(0xFF66BB6A)
                fingerOnPet -> Color(0xFFFFA726)
                else -> Color(0xFFFFB74D)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).background(petFillColor, CircleShape).pointerInput(cancelThresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fingerOnPet = true
                        voiceController.onPressStart()
                        try {
                            var totalDx = 0f
                            var totalDy = 0f
                            var dragging = false
                            var longPressTriggered = false
                            var blockedByBusy = false
                            var releasedBeforeTrigger = false
                            swipeCancelled = false
                            upward = 0f
                            val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.fastFirstOrNull { it.id == down.id } ?: continue
                                    if (!change.pressed && change.previousPressed) {
                                        releasedBeforeTrigger = true
                                        return@withTimeoutOrNull false
                                    }
                                    val delta = change.positionChange()
                                    if (delta != androidx.compose.ui.geometry.Offset.Zero) change.consume()
                                    totalDx += delta.x
                                    totalDy += delta.y
                                    if (abs(totalDx) > viewConfiguration.touchSlop || abs(totalDy) > viewConfiguration.touchSlop) {
                                        dragging = true
                                        voiceController.onPressCancel()
                                        return@withTimeoutOrNull false
                                    }
                                }
                            } == null
                            if (longPressReached && !dragging) {
                                if (!phaseRef.value.allowsVoiceInput()) {
                                    blockedByBusy = true
                                    voiceController.onPressCancel()
                                } else {
                                    longPressTriggered = true
                                    panelManuallyHidden = false
                                    panelMode = PanelMode.Expanded
                                    voiceController.onLongPressTriggered()
                                }
                            }
                            while (!releasedBeforeTrigger) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.fastFirstOrNull { it.id == down.id } ?: continue
                                if (!change.pressed && change.previousPressed) break
                                val delta = change.positionChange()
                                if (delta != androidx.compose.ui.geometry.Offset.Zero) change.consume()
                                if (dragging) {
                                    petOffsetX = (petOffsetX - delta.x).coerceIn(0f, maxPetOffsetX)
                                    petOffsetY = (petOffsetY - delta.y).coerceIn(0f, maxPetOffsetY)
                                    onPetOffsetChanged(petOffsetX.toInt(), petOffsetY.toInt())
                                } else if (longPressTriggered && delta.y < 0) {
                                    upward += -delta.y
                                    if (upward >= cancelThresholdPx) swipeCancelled = true
                                }
                            }
                            when {
                                dragging -> Unit
                                longPressTriggered -> if (swipeCancelled) voiceController.onPressCancel() else voiceController.onPressEnd()
                                blockedByBusy -> Unit
                                else -> voiceController.onPressEnd()
                            }
                        } finally {
                            fingerOnPet = false
                        }
                    }
                }
            ) {
                Text(text = "\uD83D\uDC3E", style = MaterialTheme.typography.titleMedium)
                if (petListening) {
                    Box(modifier = Modifier.size(8.dp).align(Alignment.TopEnd).background(Color(0xFF00C853), CircleShape))
                }
                if (waitingServer) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp).align(Alignment.TopEnd),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }

    if (panelMode == PanelMode.Hidden) {
        Box(modifier = Modifier.wrapContentSize().padding(20.dp)) { PetControlsColumn() }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelMaxHeight = maxHeight * 0.75f
            AnimatedVisibility(
                visible = panelMode == PanelMode.Expanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 5 }),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = panelMaxHeight),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E))
                ) {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(if (endedByUserAbort) R.string.pet_stopped_title else R.string.pet_result_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Row {
                                TextButton(onClick = { panelMode = PanelMode.Minimized }) { Text("—", color = Color.White) }
                                TextButton(onClick = { panelMode = PanelMode.Hidden; panelManuallyHidden = true }) { Text("×", color = Color.White) }
                            }
                        }
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier.fillMaxWidth().heightIn(max = panelMaxHeight - 140.dp)
                        ) {
                            itemsIndexed(chatHistory) { _, bubble ->
                                val bg = if (bubble.role == "user") Color(0xFF424242) else Color(0xFF2D2D2D)
                                Text(
                                    text = bubble.content.ifBlank { "…" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(bg, RoundedCornerShape(10.dp)).padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = panelMode == PanelMode.Minimized,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
            ) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clickable { panelMode = PanelMode.Expanded },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E))
                ) {
                    Text(
                        text = liveAssistantMarkdown.ifBlank { "聊天记录（点击展开）" }.replace('\n', ' ').take(28),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).offset { IntOffset(-petOffsetX.toInt(), -petOffsetY.toInt()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { PetControlsColumn() }
        }
    }
}

