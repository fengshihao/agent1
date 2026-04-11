package com.dynamicui.demo.pet.logic.business

import android.content.Context
import com.agent1.javaagent.tool.AgentTool

/** 供 [AgentSessionCoordinator] 注入；无头场景可换为空列表或缩减工具集。 */
fun interface PetAgentToolSupplier {
    fun buildTools(appContext: Context): List<AgentTool>
}
