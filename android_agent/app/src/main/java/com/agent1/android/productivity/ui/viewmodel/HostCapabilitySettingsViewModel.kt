package com.agent1.android.productivity.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.agent1.android.productivity.logic.business.HostCapabilitySettingsCoordinator
import com.agent1.android.productivity.logic.business.HostCapabilitySettingsState
import kotlinx.coroutines.flow.StateFlow

class HostCapabilitySettingsViewModel(
    private val coordinator: HostCapabilitySettingsCoordinator,
) : ViewModel() {

    val state: StateFlow<HostCapabilitySettingsState> = coordinator.state

    fun setCalendarEnabled(enabled: Boolean) = coordinator.setCalendarEnabled(enabled)

    fun setLocationEnabled(enabled: Boolean) = coordinator.setLocationEnabled(enabled)

    fun setContactsEnabled(enabled: Boolean) = coordinator.setContactsEnabled(enabled)
}
