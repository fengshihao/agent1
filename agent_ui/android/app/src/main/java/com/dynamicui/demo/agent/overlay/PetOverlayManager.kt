package com.dynamicui.demo.agent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.dynamicui.demo.agent.asr.DashScopeAsrTransport
import com.dynamicui.demo.agent.service.AgentForegroundService
import com.dynamicui.demo.agent.voice.core.VoiceInputController
import com.dynamicui.demo.agent.voice.core.VoiceInputSignal
import com.dynamicui.demo.agent.voice.core.VoiceInputState
import kotlinx.coroutines.flow.collectLatest

/**
 * Compose 1.6+ 要求宿主 View 同时具备 [LifecycleOwner] 与 [SavedStateRegistryOwner]；悬浮窗里的 [ComposeView] 默认没有。
 */
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

class PetOverlayManager(private val appContext: Context) {

    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private val main = Handler(Looper.getMainLooper())

    private val layoutType: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private val overlayFlags: Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private fun paramsForExpanded(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun paramsForPetOnly(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = 0
        }
    }

    private fun applyWindowMode(expanded: Boolean) {
        val view = composeView ?: return
        val params = if (expanded) paramsForExpanded() else paramsForPetOnly()
        if (view.parent == null) {
            wm.addView(view, params)
        } else {
            wm.updateViewLayout(view, params)
        }
    }

    fun show(service: AgentForegroundService) {
        if (composeView != null) return
        val view = ComposeView(appContext)
        val host = OverlayComposeHost()
        host.attach()
        view.setViewTreeLifecycleOwner(host)
        view.setViewTreeSavedStateRegistryOwner(host)
        view.setContent {
            MaterialTheme {
                PetOverlayScreen(
                    service = service,
                    runOnMain = { block -> main.post { block() } },
                    onResultPanelVisibilityChanged = { expanded ->
                        main.post { applyWindowMode(expanded) }
                    }
                )
            }
        }
        composeView = view
        applyWindowMode(expanded = false)
    }

    fun hide() {
        val v = composeView ?: return
        if (v.parent != null) {
            wm.removeView(v)
        }
        composeView = null
    }
}

@Composable
private fun PetOverlayScreen(
    service: AgentForegroundService,
    runOnMain: (() -> Unit) -> Unit,
    onResultPanelVisibilityChanged: (expanded: Boolean) -> Unit
) {
    val ctx = LocalContext.current
    var assistantMarkdown by remember { mutableStateOf("") }
    var showPanel by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(AgentSessionPhase.Idle) }
    var lastUserUtterance by remember { mutableStateOf("") }
    var endedByUserAbort by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val transport = remember(scope) { DashScopeAsrTransport(scope, runOnMain) }
    val voiceController = remember(transport, service) {
        VoiceInputController(transport) { text -> service.submitUserMessage(text) }
    }
    var voiceState by remember { mutableStateOf(VoiceInputState.Idle) }
    val serviceRef = rememberUpdatedState(service)
    val phaseRef = rememberUpdatedState(phase)

    fun dismissPanelToIdleIfTerminal() {
        showPanel = false
        if (phase == AgentSessionPhase.Done || phase == AgentSessionPhase.Error) {
            phase = AgentSessionPhase.Idle
            endedByUserAbort = false
            statusLine = ""
        }
    }

    LaunchedEffect(showPanel) {
        onResultPanelVisibilityChanged(showPanel)
    }

    LaunchedEffect(voiceController) {
        voiceController.signals.collectLatest { signal ->
            when (signal) {
                is VoiceInputSignal.StateChanged -> {
                    voiceState = signal.state
                    phase = when (signal.state) {
                        VoiceInputState.Idle ->
                            if (phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming) {
                                phase
                            } else {
                                AgentSessionPhase.Idle
                            }
                        VoiceInputState.Pressing -> phase
                        VoiceInputState.Listening -> AgentSessionPhase.Listening
                        VoiceInputState.Transcribing -> AgentSessionPhase.Transcribing
                        VoiceInputState.Submitting -> AgentSessionPhase.Sending
                        VoiceInputState.Error -> AgentSessionPhase.Error
                    }
                }
                is VoiceInputSignal.PartialText -> {
                    statusLine = signal.text.ifBlank { ctx.getString(R.string.pet_phase_listening) }
                }
                is VoiceInputSignal.FinalText -> {
                    statusLine = signal.text
                }
                is VoiceInputSignal.Submitted -> {
                    lastUserUtterance = signal.text
                    statusLine = ""
                    phase = AgentSessionPhase.Sending
                }
                is VoiceInputSignal.Error -> {
                    statusLine = signal.message
                    phase = AgentSessionPhase.Error
                }
                is VoiceInputSignal.Cancelled -> {
                    statusLine = ctx.getString(R.string.pet_cancelled)
                    phase = AgentSessionPhase.Idle
                }
                VoiceInputSignal.Busy -> {
                    statusLine = ctx.getString(R.string.pet_phase_busy_hint)
                }
            }
        }
    }

    DisposableEffect(service) {
        val listener = object : AgentForegroundService.AgentUiListener {
            override fun onAssistantStreaming(fullMarkdown: String) {
                assistantMarkdown = fullMarkdown
                if (phaseRef.value == AgentSessionPhase.Sending && fullMarkdown.isNotBlank()) {
                    phase = AgentSessionPhase.Streaming
                }
                if (phaseRef.value == AgentSessionPhase.Streaming ||
                    phaseRef.value == AgentSessionPhase.Sending
                ) {
                    showPanel = true
                }
            }

            override fun onAgentFinished(finalMarkdown: String) {
                assistantMarkdown = finalMarkdown
                phase = AgentSessionPhase.Done
                endedByUserAbort = false
                showPanel = true
            }

            override fun onAgentAborted(partialMarkdown: String) {
                assistantMarkdown = partialMarkdown
                phase = AgentSessionPhase.Done
                endedByUserAbort = true
                showPanel = true
            }

            override fun onAgentError(message: String) {
                phase = AgentSessionPhase.Error
                endedByUserAbort = false
                statusLine = message
                showPanel = true
                assistantMarkdown = "**错误**\n\n$message"
            }
        }
        service.addUiListener(listener)
        onDispose {
            voiceController.stopAll()
            service.removeUiListener(listener)
        }
    }

    val density = LocalDensity.current
    val cancelThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }

    val phaseHintMain = stringResource(
        when (phase) {
            AgentSessionPhase.Idle -> R.string.pet_phase_idle
            AgentSessionPhase.Listening -> R.string.pet_phase_listening
            AgentSessionPhase.Transcribing -> R.string.pet_phase_transcribing
            AgentSessionPhase.Sending -> R.string.pet_phase_sending_main
            AgentSessionPhase.Streaming -> R.string.pet_phase_streaming_main
            AgentSessionPhase.Done ->
                if (endedByUserAbort) R.string.pet_stopped_title
                else R.string.pet_phase_idle
            AgentSessionPhase.Error -> R.string.pet_phase_idle
        }
    )
    val phaseHintSub = stringResource(
        when (phase) {
            AgentSessionPhase.Sending -> R.string.pet_phase_sending_sub
            AgentSessionPhase.Streaming -> R.string.pet_phase_streaming_sub
            else -> R.string.pet_phase_sending_sub
        }
    )
    val showPhaseSub = phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming
    val statusCompact = remember(statusLine) {
        statusLine
            .replace('\n', ' ')
            .let { if (it.length > 24) it.take(24) + "..." else it }
    }

    @Composable
    fun PetControlsColumn() {
        var fingerOnPet by remember { mutableStateOf(false) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .width(180.dp)
                .defaultMinSize(minHeight = 260.dp)
        ) {
            if (statusLine.isNotBlank()) {
                Text(
                    text = statusCompact,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Text(
                text = phaseHintMain,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
            if (showPhaseSub) {
                Text(
                    text = phaseHintSub,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .background(Color(0x66000000), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                )
            }
            if (phase.isLlmActive() && !showPanel) {
                TextButton(
                    onClick = { serviceRef.value.abortAgent() },
                    modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        stringResource(R.string.pet_stop_generation),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            var upward by remember { mutableFloatStateOf(0f) }
            var swipeCancelled by remember { mutableStateOf(false) }
            val petListening =
                voiceState == VoiceInputState.Listening ||
                    voiceState == VoiceInputState.Transcribing
            val petFillColor = when {
                petListening -> Color(0xFF66BB6A)
                fingerOnPet -> Color(0xFFFFA726)
                else -> Color(0xFFFFB74D)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(petFillColor, CircleShape)
                    // 切勿把 phase 放进 keys：进入 Listening 会重启协程并立刻 stop ASR，导致服务端报协议错误。
                    .pointerInput(cancelThresholdPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fingerOnPet = true
                            voiceController.onPressStart()
                            try {
                                val longPress = awaitLongPressOrCancellation(down.id)
                                if (longPress == null) {
                                    // 短按仅结束 Pressing，不启动语音。
                                    voiceController.onPressEnd()
                                    return@awaitEachGesture
                                }
                                if (!phaseRef.value.allowsVoiceInput()) {
                                    statusLine = ctx.getString(R.string.pet_phase_busy_hint)
                                    voiceController.onPressCancel()
                                    return@awaitEachGesture
                                }
                                swipeCancelled = false
                                upward = 0f
                                statusLine = ""
                                voiceController.onLongPressTriggered()
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.fastFirstOrNull { it.id == down.id }
                                            ?: continue
                                        if (!change.pressed && change.previousPressed) {
                                            break
                                        }
                                        val drag = change.positionChange()
                                        change.consume()
                                        if (drag.y < 0) {
                                            upward += -drag.y
                                            if (upward >= cancelThresholdPx) {
                                                swipeCancelled = true
                                            }
                                        }
                                    }
                                } finally {
                                    if (swipeCancelled) {
                                        voiceController.onPressCancel()
                                    } else {
                                        voiceController.onPressEnd()
                                    }
                                }
                            } finally {
                                fingerOnPet = false
                            }
                        }
                    }
            ) {
                Text(text = "\uD83D\uDC3E", style = MaterialTheme.typography.headlineSmall)
                if (petListening || phase.isLlmActive()) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }

    if (showPanel) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable {
                        dismissPanelToIdleIfTerminal()
                    }
            )
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 480.dp)
                    .padding(8.dp)
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            when {
                                endedByUserAbort -> R.string.pet_stopped_title
                                else -> R.string.pet_result_title
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming) {
                        Text(
                            text = stringResource(
                                if (phase == AgentSessionPhase.Sending)
                                    R.string.pet_phase_sending_main
                                else
                                    R.string.pet_phase_streaming_main
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = assistantMarkdown.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (phase == AgentSessionPhase.Error && lastUserUtterance.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    statusLine = ""
                                    phase = AgentSessionPhase.Sending
                                    val ok = serviceRef.value.submitUserMessage(lastUserUtterance)
                                    if (!ok) {
                                        phase = AgentSessionPhase.Error
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.pet_retry))
                            }
                        }
                        if (phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming) {
                            TextButton(onClick = { serviceRef.value.abortAgent() }) {
                                Text(stringResource(R.string.pet_stop_generation))
                            }
                        }
                        TextButton(onClick = { dismissPanelToIdleIfTerminal() }) {
                            Text(
                                if (phase == AgentSessionPhase.Sending || phase == AgentSessionPhase.Streaming)
                                    stringResource(R.string.pet_dismiss_panel)
                                else
                                    stringResource(R.string.pet_close)
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PetControlsColumn()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PetControlsColumn()
        }
    }
}
