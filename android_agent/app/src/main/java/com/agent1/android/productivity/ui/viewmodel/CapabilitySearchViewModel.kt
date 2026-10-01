package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.productivity.logic.business.ProductivityAgentGateway
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import com.agent1.javaagent.capability.CapabilitySearchView
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CapabilityHitUi(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String,
    val entry: String,
    val docPath: String,
    val platforms: String,
    val source: String,
    val loaded: Boolean,
    val availability: String,
)

data class DisabledMcpUi(
    val name: String,
    val url: String,
    val description: String,
)

data class CapabilitySearchUiState(
    val query: String = "",
    val selectedKinds: Set<String> = emptySet(),
    val limit: Int = CapabilitySearchView.DEFAULT_LIMIT,
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val searched: Boolean = false,
    val modelText: String = "",
    val hostPlatform: String = "",
    val visible: List<CapabilityHitUi> = emptyList(),
    val hiddenByPlatform: List<CapabilityHitUi> = emptyList(),
    val disabledMcp: List<DisabledMcpUi> = emptyList(),
)

class CapabilitySearchViewModel(
    private val appContext: Context,
) : ViewModel() {

    private val gateway: ProductivityAgentGateway
        get() = ProductivityGatewayProvider.get(appContext.applicationContext)

    private val _state = MutableStateFlow(CapabilitySearchUiState())
    val state: StateFlow<CapabilitySearchUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun toggleKind(kind: String) {
        _state.update { current ->
            val next = current.selectedKinds.toMutableSet()
            if (!next.add(kind)) {
                next.remove(kind)
            }
            current.copy(selectedKinds = next)
        }
    }

    fun setLimit(limit: Int) {
        val clamped = limit.coerceIn(1, CapabilitySearchView.MAX_LIMIT)
        _state.update { it.copy(limit = clamped) }
    }

    fun search() {
        val current = _state.value
        if (current.busy || current.query.isBlank()) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorMessage = null) }
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val kinds = CapabilitySearchView.KINDS.filter { current.selectedKinds.contains(it) }
                    Result.success(gateway.searchCapabilities(current.query.trim(), kinds, current.limit))
                } catch (error: ExecutionException) {
                    Result.failure(error.cause ?: error)
                } catch (error: IllegalArgumentException) {
                    Result.failure(error)
                } catch (error: IllegalStateException) {
                    Result.failure(error)
                }
            }
            outcome.fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            busy = false,
                            searched = true,
                            modelText = result.modelText(),
                            hostPlatform = result.hostPlatform(),
                            visible = result.visible().map { hit -> hit.toUi() },
                            hiddenByPlatform = result.hiddenByPlatform().map { hit -> hit.toUi() },
                            disabledMcp = result.disabledMcp().map { server ->
                                DisabledMcpUi(server.name(), server.url(), server.description())
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            searched = true,
                            errorMessage = error.message ?: "检索失败",
                        )
                    }
                },
            )
        }
    }

    companion object {
        val DEFAULT_LIMIT: Int = CapabilitySearchView.DEFAULT_LIMIT
        val MAX_LIMIT: Int = CapabilitySearchView.MAX_LIMIT
        val VISIBLE: String = CapabilitySearchView.VISIBLE

        val KIND_LABELS: Map<String, String> = mapOf(
            "caps" to "Caps",
            "builtin" to "内置",
            "catalog_script" to "catalog 脚本",
            "catalog_lib" to "catalog 库",
            "native" to "native",
            "skill" to "Skill",
            "mcp" to "MCP",
            "bridge_tool" to "桥接",
            "agent_tool" to "外层工具",
            "doc" to "文档",
        )

        fun kindChoices(): List<Pair<String, String>> {
            return CapabilitySearchView.KINDS.map { id -> id to (KIND_LABELS[id] ?: id) }
        }

        fun availabilityLabel(availability: String): String {
            return when (availability) {
                CapabilitySearchView.LISTED_BUT_UNUSABLE -> "已索引，当前不可用"
                CapabilitySearchView.PLATFORM_HIDDEN -> "当前平台不可用"
                else -> "可用"
            }
        }
    }
}

private fun CapabilitySearchView.HitView.toUi(): CapabilityHitUi {
    return CapabilityHitUi(
        id = id(),
        kind = kind(),
        title = title(),
        summary = summary(),
        entry = entry(),
        docPath = docPath(),
        platforms = platforms(),
        source = source(),
        loaded = loaded(),
        availability = availability(),
    )
}
