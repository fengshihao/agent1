package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.productivity.logic.data.HostCapabilityPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HostCapabilitySettingsCoordinator(context: Context) {

    private val prefs = HostCapabilityPreferences(context.applicationContext)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HostCapabilitySettingsState> = _state.asStateFlow()

    fun load(): HostCapabilitySettingsState = HostCapabilitySettingsState(
        calendarEnabled = prefs.isCalendarEnabled(),
        locationEnabled = prefs.isLocationEnabled(),
        contactsEnabled = prefs.isContactsEnabled(),
        remindersEnabled = prefs.isRemindersEnabled(),
    )

    fun setCalendarEnabled(enabled: Boolean) {
        prefs.setCalendarEnabled(enabled)
        _state.value = load()
    }

    fun setLocationEnabled(enabled: Boolean) {
        prefs.setLocationEnabled(enabled)
        _state.value = load()
    }

    fun setContactsEnabled(enabled: Boolean) {
        prefs.setContactsEnabled(enabled)
        _state.value = load()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.setRemindersEnabled(enabled)
        _state.value = load()
    }
}

data class HostCapabilitySettingsState(
    val calendarEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val contactsEnabled: Boolean = false,
    val remindersEnabled: Boolean = false,
)
