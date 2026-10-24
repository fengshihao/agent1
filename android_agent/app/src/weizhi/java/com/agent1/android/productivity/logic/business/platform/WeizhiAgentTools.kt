package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.WorkspaceToolProvider
import com.agent1.javaagent.tool.anno.AnnotatedTools
import com.agent1.javaagent.tool.workspace.BashTool
import com.agent1.javaagent.tool.workspace.GlobTool
import com.agent1.javaagent.tool.workspace.GrepTool
import com.agent1.javaagent.tool.workspace.ZipTools
import com.agent1.javaagent.workspace.WorkspaceSandbox
import com.weizhi.agent.web.WebViewAgentExtension
import java.nio.file.Path

/**
 * 集成 Weizhi 时追加的工具：搜索、压缩、bash 在 agent_core；WebView 用系统 WebView。
 * 脚本 MCP 走引擎 {@code mcp.connect}。Skill 加载统一走宿主注册的 {@code load_skill}
 * （assets 源由 [WeizhiHostLoader] 经 {@code setSkillRepositories} 注入）。
 */
class WeizhiAgentTools(
    private val appContext: Context,
    private val agentRoot: Path,
) : WorkspaceToolProvider {

    override fun toolsFor(sandbox: WorkspaceSandbox): List<AgentTool> {
        val web = WebViewAgentExtension(appContext).create(sandbox)
        return AnnotatedTools.from(GrepTool(sandbox)) +
            AnnotatedTools.from(GlobTool(sandbox)) +
            AnnotatedTools.from(ZipTools(sandbox)) +
            AnnotatedTools.from(BashTool(sandbox)) +
            AnnotatedTools.from(web)
    }
}
