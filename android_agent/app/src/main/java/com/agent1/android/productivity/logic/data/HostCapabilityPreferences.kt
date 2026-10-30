package com.agent1.android.productivity.logic.data

import android.content.Context

/** 用户是否在设置中允许助手访问各原生能力（对应 OpenMinis OffloadGate 的产品闸）。 */
class HostCapabilityPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isCalendarEnabled(): Boolean = prefs.getBoolean(KEY_CALENDAR, false)

    fun setCalendarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR, enabled).apply()
    }

    fun isLocationEnabled(): Boolean = prefs.getBoolean(KEY_LOCATION, false)

    fun setLocationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION, enabled).apply()
    }

    fun isContactsEnabled(): Boolean = prefs.getBoolean(KEY_CONTACTS, false)

    fun setContactsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTACTS, enabled).apply()
    }

    fun isRemindersEnabled(): Boolean = prefs.getBoolean(KEY_REMINDERS, false)

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply()
    }

    companion object {
        private const val PREFS = "agent1_host_capabilities"
        private const val KEY_CALENDAR = "calendar"
        private const val KEY_LOCATION = "location"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_REMINDERS = "reminders"
    }
}
