package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSetupGateTest {

    @Test
    fun missingKeyAlwaysPrompts() {
        assertTrue(ApiSetupGate.needsUserKeySetup(savedInApp = false, apiKeyConfigured = false))
        assertTrue(ApiSetupGate.needsUserKeySetup(savedInApp = true, apiKeyConfigured = false))
    }

    @Test
    fun buildKeyWithoutSavePromptsAfterSend() {
        assertTrue(ApiSetupGate.needsUserKeySetup(savedInApp = false, apiKeyConfigured = true))
    }

    @Test
    fun savedAndConfiguredNeedsNoPrompt() {
        assertFalse(ApiSetupGate.needsUserKeySetup(savedInApp = true, apiKeyConfigured = true))
    }
}
