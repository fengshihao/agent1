package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.session.ProductivityAgentHost
import com.agent1.android.BuildConfig
import java.nio.file.Path

/** 按是否集成 Weizhi 子工程装配 {@link ProductivityAgentHost}。 */
object ProductivityHostAssembly {

    fun create(
        context: Context,
        agentRoot: Path,
        config: AgentRuntimeConfig,
    ): ProductivityAgentHost {
        if (!BuildConfig.WEIZHI_INTEGRATED) {
            return ProductivityAgentHost(agentRoot, config)
        }
        @Suppress("UNCHECKED_CAST")
        return Class.forName(WEIZHI_LOADER)
            .getDeclaredMethod(
                "create",
                Context::class.java,
                Path::class.java,
                AgentRuntimeConfig::class.java,
            )
            .invoke(null, context.applicationContext, agentRoot, config) as ProductivityAgentHost
    }

    private const val WEIZHI_LOADER =
        "com.agent1.android.productivity.logic.business.platform.WeizhiHostLoader"
}
