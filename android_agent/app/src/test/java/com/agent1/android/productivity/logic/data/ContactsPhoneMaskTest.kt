package com.agent1.android.productivity.logic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsPhoneMaskTest {

    @Test
    fun masksToLastFourDigits() {
        assertEquals("****1234", ContactsPhoneMask.mask("+86 138 0013 8000"))
    }

    @Test
    fun shortNumberFullyMasked() {
        assertEquals("****", ContactsPhoneMask.mask("123"))
    }
}
