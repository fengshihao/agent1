package com.dynamicui.demo.pet.logic.business

import android.content.Context
import com.agent1.javaagent.core.AgentOptions
import com.agent1.javaagent.core.AgentRuntime
import com.agent1.javaagent.core.MessageHistoryLimiter
import com.agent1.javaagent.event.AgentEvent
import com.agent1.javaagent.event.AgentEventListener
import com.agent1.javaagent.event.AgentEventType
import com.agent1.javaagent.event.EventPayloads
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient
import com.agent1.javaagent.llm.openai.OpenAiCompatibleConfig
import com.agent1.javaagent.model.AgentMessage
import com.dynamicui.demo.BuildConfig
import com.dynamicui.demo.pet.logic.data.PetVoiceAgentTooling
import com.dynamicui.demo.pet.logic.data.accessibility.core.PageContextPromptBuilder
import com.dynamicui.demo.pet.logic.data.accessibility.core.PageSnapshotStore
import com.dynamicui.demo.pet.logic.data.service.ShellContextBuilder
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class AgentSessionCoordinator(
    private val appContext: Context,
    private val eventSink: AgentRunEventSink,
    private val logger: (tag: String, message: String) -> Unit,
    private val toolSupplier: PetAgentToolSupplier = PetAgentToolSupplier { PetVoiceAgentTooling.buildTools(it) }
) : AgentEventListener {
    private var runtime: AgentRuntime? = null
    private val abortRequested = AtomicBoolean(false)
    private var hadErrorThisRun = false

    fun initRuntimeIfPossible() {
        val key = BuildConfig.DASHSCOPE_API_KEY.trim()
        if (key.isEmpty()) {
            eventSink.onAgentUiEvent(AgentUiEvent.Error("未配置 DASHSCOPE_API_KEY"))
            return
        }
        val voicePrompt = try {
            appContext.assets.open(VOICE_PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            eventSink.onAgentUiEvent(AgentUiEvent.Error("无法读取语音助手提示词: " + (e.message ?: "")))
            return
        }
        if (voicePrompt.isBlank()) {
            eventSink.onAgentUiEvent(AgentUiEvent.Error("语音助手提示词为空"))
            return
        }
        val llmClient = OpenAiCompatibleClient(
            OpenAiCompatibleConfig(
                key,
                BuildConfig.DASHSCOPE_BASE_URL.trim(),
                Duration.ofSeconds(120),
                0.2
            )
        )
        val options = AgentOptions.builder("qwen3.5-flash")
            .systemPrompt(voicePrompt)
            .maxContextMessages(MAX_CONTEXT_MESSAGES_FOR_MODEL)
            .maxTurnsPerRun(MAX_TURNS_PER_RUN)
            .maxToolCallsPerRun(MAX_TOOL_CALLS_PER_RUN)
            .tools(toolSupplier.buildTools(appContext))
            .build()
        val rt = AgentRuntime(options, llmClient)
        rt.subscribe(this)
        runtime = rt
    }

    override fun onEvent(event: AgentEvent) {
        logger("AgentEvent", "type=${event.type}")
        when (event.type) {
            AgentEventType.AGENT_START -> {
                hadErrorThisRun = false
                eventSink.onAgentUiEvent(AgentUiEvent.BusyChanged(true))
                eventSink.onAgentUiEvent(AgentUiEvent.Streaming(""))
            }
            AgentEventType.MESSAGE_UPDATE -> {
                val p = event.payload as? EventPayloads.MessageUpdate ?: return
                eventSink.onAgentUiEvent(AgentUiEvent.Streaming(p.partialMessage.content))
            }
            AgentEventType.AGENT_END -> {
                val p = event.payload as? EventPayloads.AgentEnd ?: return
                val finalText = lastAssistantContent(p.messages)
                logger("AgentEvent", "agent_end finalLen=${finalText.length}")
                eventSink.onAgentUiEvent(AgentUiEvent.BusyChanged(false))
                when {
                    abortRequested.getAndSet(false) -> {
                        hadErrorThisRun = false
                        eventSink.onAgentUiEvent(AgentUiEvent.Aborted(finalText))
                    }
                    hadErrorThisRun -> {
                        hadErrorThisRun = false
                    }
                    else -> eventSink.onAgentUiEvent(AgentUiEvent.Finished(finalText))
                }
            }
            AgentEventType.AGENT_ERROR -> {
                val p = event.payload as? EventPayloads.AgentError
                logger("AgentEvent", "agent_error=${p?.message ?: "未知错误"}")
                hadErrorThisRun = true
                eventSink.onAgentUiEvent(AgentUiEvent.BusyChanged(false))
                eventSink.onAgentUiEvent(AgentUiEvent.Error(p?.message ?: "未知错误"))
            }
            AgentEventType.TURN_START -> {
                val p = event.payload as? EventPayloads.TurnStart
                logger("AgentEvent", "turn_start index=${p?.turnIndex ?: -1}")
            }
            AgentEventType.TOOL_EXECUTION_START -> {
                val p = event.payload as? EventPayloads.ToolExecutionStart
                logger(
                    "AgentEvent",
                    "tool_start name=${p?.toolCall?.name ?: "unknown"} id=${p?.toolCall?.id ?: "unknown"}"
                )
            }
            AgentEventType.TOOL_EXECUTION_END -> {
                val p = event.payload as? EventPayloads.ToolExecutionEnd
                logger(
                    "AgentEvent",
                    "tool_end id=${p?.toolCallId ?: "unknown"} isError=${p?.isError ?: false}"
                )
            }
            else -> Unit
        }
    }

    fun submitUserMessage(text: String): Boolean {
        val rt = runtime ?: run {
            eventSink.onAgentUiEvent(AgentUiEvent.Error("Agent 未初始化，请检查 API Key"))
            return false
        }
        val msg = text.trim()
        if (msg.isEmpty()) return false
        val pageContext = PageContextPromptBuilder.build(PageSnapshotStore.latest())
        val shellContext = ShellContextBuilder.build()
        val enriched = buildString {
            appendLine("[当前环境]")
            appendLine(pageContext)
            appendLine()
            appendLine(shellContext)
            appendLine()
            appendLine("[用户请求]")
            append(msg)
        }
        return try {
            pruneInMemoryHistoryIfNeeded(rt)
            rt.prompt(enriched)
            true
        } catch (_: IllegalStateException) {
            eventSink.onAgentUiEvent(AgentUiEvent.Error("上一轮尚未结束，请稍候"))
            false
        } catch (e: Exception) {
            eventSink.onAgentUiEvent(AgentUiEvent.Error(e.message ?: "请求失败"))
            false
        }
    }

    fun abortAgent() {
        abortRequested.set(true)
        runtime?.abort()
    }

    fun close() {
        runtime?.close()
        runtime = null
    }

    private fun pruneInMemoryHistoryIfNeeded(rt: AgentRuntime) {
        val messages = rt.stateSnapshot.messages
        if (messages.size <= MAX_PERSISTED_MESSAGES_IN_MEMORY) return
        val kept = MessageHistoryLimiter.limitTail(messages, PERSISTED_MESSAGES_TAIL_KEEP)
        rt.replaceMessages(kept)
    }

    private fun lastAssistantContent(messages: List<AgentMessage>): String {
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (AgentMessage.ROLE_ASSISTANT == m.role) {
                return m.content
            }
        }
        return ""
    }

    companion object {
        private const val VOICE_PROMPT_ASSET = "prompts/voice_assistant_system_prompt.txt"
        private const val MAX_CONTEXT_MESSAGES_FOR_MODEL = 60
        private const val MAX_TURNS_PER_RUN = 12
        private const val MAX_TOOL_CALLS_PER_RUN = 24
        private const val MAX_PERSISTED_MESSAGES_IN_MEMORY = 220
        private const val PERSISTED_MESSAGES_TAIL_KEEP = 160
    }
}
