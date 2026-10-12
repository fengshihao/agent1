package com.agent1.android.productivity.logic.business

/** 是否在对话里展示 DeepSeek 引导（顶栏红字 + 禁用输入都不走这条）。 */
object ApiSetupGate {

    fun needsUserKeySetup(savedInApp: Boolean, apiKeyConfigured: Boolean): Boolean {
        if (!apiKeyConfigured) return true
        return !savedInApp
    }
}
