package com.agent1.android.productivity.logic.data

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.android.productivity.logic.data.config.AgentRuntimePreferencesMerge
import com.agent1.android.productivity.logic.data.config.AgentRuntimePreferencesStore

/** 将 App 内配置 + BuildConfig 编译注入转为 {@link AgentRuntimeConfig}。 */
object AndroidAgentRuntimeConfig {

    fun load(context: Context): AgentRuntimeConfig {
        val prefs = AgentRuntimePreferencesStore(context.applicationContext).read()
        val props = AgentRuntimePreferencesMerge.toProperties(prefs)
        return AgentRuntimeConfig.fromProperties(props)
    }
}
