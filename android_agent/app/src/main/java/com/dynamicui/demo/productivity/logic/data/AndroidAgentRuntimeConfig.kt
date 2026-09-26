package com.dynamicui.demo.productivity.logic.data

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.dynamicui.demo.productivity.logic.data.config.AgentRuntimePreferencesMerge
import com.dynamicui.demo.productivity.logic.data.config.AgentRuntimePreferencesStore

/** 将 App 内配置 + BuildConfig 编译注入转为 {@link AgentRuntimeConfig}。 */
object AndroidAgentRuntimeConfig {

    fun load(context: Context): AgentRuntimeConfig {
        val prefs = AgentRuntimePreferencesStore(context.applicationContext).read()
        val props = AgentRuntimePreferencesMerge.toProperties(prefs)
        return AgentRuntimeConfig.fromProperties(props)
    }
}
