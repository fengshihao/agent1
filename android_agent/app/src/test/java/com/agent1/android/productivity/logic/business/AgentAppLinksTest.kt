package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentAppLinksTest {

    @Test
    fun modelSettingsLinkMatchesCustomScheme() {
        assertEquals("agent1://model-settings", AgentAppLinks.MODEL_SETTINGS)
    }
}