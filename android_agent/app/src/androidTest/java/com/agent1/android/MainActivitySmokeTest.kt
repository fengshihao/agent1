package com.agent1.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机连通：启动后可见会话列表，点新建能进入对话页（不调 LLM）。
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sessionListVisibleAndCreateOpensChat() {
        composeRule.onNodeWithText("会话").assertIsDisplayed()
        composeRule.onNodeWithText("新建").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("← 列表").assertIsDisplayed()
    }
}
