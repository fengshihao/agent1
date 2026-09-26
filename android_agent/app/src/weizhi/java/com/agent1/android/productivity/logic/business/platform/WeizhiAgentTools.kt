package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.WorkspaceToolProvider
import com.agent1.javaagent.weizhi.WeizhiToolkitAdapters
import com.agent1.javaagent.workspace.WorkspaceSandbox
import com.weizhi.agent.mcp.McpAgentExtension
import com.weizhi.agent.sandbox.WorkspaceSandbox as WeizhiSandbox
import com.weizhi.agent.skill.AssetSkillRepository
import com.weizhi.agent.skill.CompositeSkillRepository
import com.weizhi.agent.skill.FileSystemSkillRepository
import com.weizhi.agent.skill.LoadSkillTool
import com.weizhi.agent.tool.AgentToolkit
import com.weizhi.agent.tool.builtin.BashTool
import com.weizhi.agent.tool.builtin.GlobTool
import com.weizhi.agent.tool.builtin.GrepTool
import com.weizhi.agent.tool.builtin.ZipTools
import com.weizhi.agent.web.WebViewAgentExtension

/**
 * Android 侧 Weizhi 工具环：搜索、压缩、bash、skill，以及 WebView / MCP。
 * 文件读写仍走 Agent1 工作区工具。
 */
class WeizhiAgentTools(
    private val appContext: Context,
) : WorkspaceToolProvider {

    override fun toolsFor(sandbox: WorkspaceSandbox): List<AgentTool> {
        val root = sandbox.root
        val weizhiSandbox = WeizhiSandbox(root)
        val toolkit = AgentToolkit()
        toolkit.registerTool(GrepTool(weizhiSandbox))
        toolkit.registerTool(GlobTool(weizhiSandbox))
        toolkit.registerTool(ZipTools(weizhiSandbox))
        toolkit.registerTool(BashTool(weizhiSandbox))
        toolkit.registerTool(
            LoadSkillTool(
                CompositeSkillRepository(
                    AssetSkillRepository(appContext, "agent_skills"),
                    FileSystemSkillRepository(root.resolve("skills")),
                ),
            ),
        )
        WebViewAgentExtension(appContext).register(toolkit, weizhiSandbox)
        McpAgentExtension(appContext.filesDir.toPath()).register(toolkit, weizhiSandbox)
        return WeizhiToolkitAdapters.toAgentTools(toolkit)
    }
}
