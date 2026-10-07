package com.agent1.android.productivity.logic.business.platform

import android.content.Context
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.WorkspaceToolProvider
import com.agent1.javaagent.tool.anno.AnnotatedTools
import com.agent1.javaagent.tool.skill.LoadSkillTool
import com.agent1.javaagent.tool.workspace.BashTool
import com.agent1.javaagent.tool.workspace.GlobTool
import com.agent1.javaagent.tool.workspace.GrepTool
import com.agent1.javaagent.tool.workspace.ZipTools
import com.agent1.javaagent.weizhi.WeizhiSandboxFactory
import com.agent1.javaagent.weizhi.WeizhiToolkitAdapters
import com.agent1.javaagent.workspace.WorkspaceSandbox
import com.weizhi.agent.skill.AssetSkillRepository
import com.weizhi.agent.skill.CompositeSkillRepository
import com.weizhi.agent.skill.FileSystemSkillRepository
import com.weizhi.agent.tool.AgentToolkit
import com.weizhi.agent.web.WebViewAgentExtension
import java.nio.file.Path

/**
 * Android 侧 Weizhi 工具环：搜索、压缩、bash、skill，以及 WebView。
 * MCP 调用走脚本 {@code $mcp}（引擎 {@code mcp.connect}），不在这里注册 Java 工具、不写 workspace/.mcp。
 * 文件读写仍走 Agent1 工作区工具。
 */
class WeizhiAgentTools(
    private val appContext: Context,
    private val agentRoot: Path,
) : WorkspaceToolProvider {

    override fun toolsFor(sandbox: WorkspaceSandbox): List<AgentTool> {
        val weizhiSandbox = WeizhiSandboxFactory.forProductivity(sandbox)
        val toolkit = AgentToolkit()
        WebViewAgentExtension(appContext).register(toolkit, weizhiSandbox)
        val root = agentRoot.toAbsolutePath().normalize()
        val skills = CompositeSkillRepository(
            AssetSkillRepository(appContext, "agent_skills"),
            FileSystemSkillRepository(sandbox.root.resolve("skills")),
            FileSystemSkillRepository(root.resolve("shared/catalog/skills")),
            FileSystemSkillRepository(root.resolve("shared/local/skills")),
        )
        return AnnotatedTools.from(GrepTool(sandbox)) +
            AnnotatedTools.from(GlobTool(sandbox)) +
            AnnotatedTools.from(ZipTools(sandbox)) +
            AnnotatedTools.from(BashTool(sandbox)) +
            AnnotatedTools.from(LoadSkillTool { skillId, path -> skills.readResource(skillId, path) }) +
            WeizhiToolkitAdapters.toAgentTools(toolkit, sandbox, true)
    }
}
