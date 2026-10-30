package com.agent1.android.productivity.logic.data

/** 通讯录号码脱敏（纯函数，便于单测）。 */
object ContactsPhoneMask {

    fun mask(number: String): String {
        val digits = number.filter { it.isDigit() }
        if (digits.length <= 4) {
            return "****"
        }
        return "****${digits.takeLast(4)}"
    }
}
