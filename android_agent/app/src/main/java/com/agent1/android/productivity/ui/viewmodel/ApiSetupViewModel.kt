package com.agent1.android.productivity.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.DeepSeekApiKeyFormat
import com.agent1.android.productivity.logic.business.DeepSeekKeyImportResult
import com.agent1.android.productivity.logic.business.ModelSettingsCoordinator
import com.agent1.android.productivity.logic.business.i18n.AppLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ApiSetupUiState(
    val draft: String = "",
    val isSaving: Boolean = false,
    val notice: String? = null,
    val noticeIsError: Boolean = false,
    val readyToEnter: Boolean = false,
)

class ApiSetupViewModel(
    private val coordinator: ModelSettingsCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(ApiSetupUiState())
    val state: StateFlow<ApiSetupUiState> = _state.asStateFlow()

    fun onDraftChange(value: String) {
        _state.value = _state.value.copy(draft = value, notice = null, noticeIsError = false)
    }

    fun consumeReadyToEnter() {
        _state.value = _state.value.copy(readyToEnter = false)
    }

    fun apply() {
        val raw = _state.value.draft
        if (raw.isBlank()) {
            _state.value = _state.value.copy(
                notice = AppLocalization.getString(R.string.setup_key_empty),
                noticeIsError = true,
            )
            return
        }
        if (!DeepSeekApiKeyFormat.isPlausible(raw)) {
            _state.value = _state.value.copy(
                notice = AppLocalization.getString(R.string.setup_key_invalid),
                noticeIsError = true,
            )
            return
        }
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isSaving = true,
                notice = AppLocalization.getString(R.string.setup_key_checking),
                noticeIsError = false,
                readyToEnter = false,
            )
            val result = withContext(Dispatchers.IO) { coordinator.importDeepSeekApiKey(raw) }
            val notice = when (result) {
                DeepSeekKeyImportResult.INVALID ->
                    AppLocalization.getString(R.string.setup_key_invalid)
                DeepSeekKeyImportResult.SAVED_OK ->
                    AppLocalization.getString(R.string.setup_key_imported)
                DeepSeekKeyImportResult.SAVED_UNVERIFIED ->
                    AppLocalization.getString(R.string.setup_key_saved_unverified)
            }
            _state.value = _state.value.copy(
                isSaving = false,
                notice = notice,
                noticeIsError = result != DeepSeekKeyImportResult.SAVED_OK,
                readyToEnter = result != DeepSeekKeyImportResult.INVALID,
            )
        }
    }
}
