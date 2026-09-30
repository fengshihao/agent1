package com.agent1.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机连通：启动后直接进入对话；会话列表收在左侧抽屉里（不调 LLM）。
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatVisibleAndSessionDrawerOpens() {
        composeRule.onNodeWithText("输入消息…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("会话列表").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("会话列表").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("新建对话").assertIsDisplayed()
    }
}
