package com.dynamicui.demo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机/模拟器连通测试：启动 MainActivity，断言本地 Tab 可见（不调用网络、不 mock LLM）。
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun localSampleTabIsVisibleOnLaunch() {
        composeRule.onNodeWithText("本地样例").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic UI v1 (Local JSON)").assertIsDisplayed()
    }
}
