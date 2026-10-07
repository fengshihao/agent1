package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RegisteredToolUi(
    val name: String,
    val description: String,
    val parametersSchema: String,
)

data class KeywordGlance(
    val word: String,
    val promptHits: Int,
    val toolHits: Int,
)

data class SystemPromptUiState(
    val busy: Boolean = true,
    val errorMessage: String? = null,
    val prompt: String = "",
    val tools: List<RegisteredToolUi> = emptyList(),
    val find: String = "",
    val glances: List<KeywordGlance> = emptyList(),
    val copyMessage: String? = null,
)

class SystemPromptViewModel(
    private val appContext: Context,
    private val sessionId: String,
) : ViewModel() {

    private val gateway: ProductivityAgentGateway
        get() = ProductivityGatewayProvider.get(appContext.applicationContext)

    private val _state = MutableStateFlow(SystemPromptUiState())
    val state: StateFlow<SystemPromptUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onFindChange(value: String) {
        _state.update { it.copy(find = value, copyMessage = null) }
    }

    fun useFind(word: String) {
        _state.update { it.copy(find = word, copyMessage = null) }
    }

    fun onCopied() {
        _state.update { it.copy(copyMessage = "已复制系统提示词") }
    }

    fun refresh() {
        if (sessionId.isBlank()) {
            _state.value = SystemPromptUiState(
                busy = false,
                errorMessage = "先打开或新建一个会话，再查看发给模型的系统提示词。",
            )
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorMessage = null, copyMessage = null) }
            val outcome = withContext(Dispatchers.IO) {
                try {
                    Result.success(gateway.systemPromptSnapshot(sessionId))
                } catch (error: ExecutionException) {
                    Result.failure(error.cause ?: error)
                } catch (error: IllegalArgumentException) {
                    Result.failure(error)
                } catch (error: IllegalStateException) {
                    Result.failure(error)
                }
            }
            outcome.fold(
                onSuccess = { snapshot ->
                    val tools = snapshot.tools.map { tool ->
                        RegisteredToolUi(tool.name, tool.description, tool.parametersSchema)
                    }
                    _state.update { current ->
                        current.copy(
                            busy = false,
                            prompt = snapshot.prompt,
                            tools = tools,
                            glances = keywordGlance(snapshot.prompt, tools, KEYWORDS),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            errorMessage = error.message ?: "读取系统提示词失败",
                        )
                    }
                },
            )
        }
    }

    companion object {
        val KEYWORDS: List<String> = listOf(
            "QuickJS",
            "WebView",
            "npm",
            "目录",
            "workspace",
            "run_js",
        )
    }
}

internal fun keywordGlance(
    prompt: String,
    tools: List<RegisteredToolUi>,
    words: List<String>,
): List<KeywordGlance> {
    return words.map { word ->
        val toolText = tools.joinToString("\n") { tool ->
            tool.name + "\n" + tool.description + "\n" + tool.parametersSchema
        }
        KeywordGlance(
            word = word,
            promptHits = countIgnoreCase(prompt, word),
            toolHits = countIgnoreCase(toolText, word),
        )
    }
}

internal fun countIgnoreCase(text: String, needle: String): Int {
    if (needle.isEmpty() || text.isEmpty()) {
        return 0
    }
    val haystack = text.lowercase()
    val token = needle.lowercase()
    var count = 0
    var from = 0
    while (from <= haystack.length - token.length) {
        val index = haystack.indexOf(token, from)
        if (index < 0) {
            break
        }
        count += 1
        from = index + token.length
    }
    return count
}
