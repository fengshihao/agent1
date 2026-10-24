package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.weizhi.desktop.DesktopWebViewExecTool;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 桌面生产力路径追加的 Weizhi 工具环。
 * grep、glob、zip、bash、桌面 {@code webview_exec} 都是 Agent1 的 {@code @Tool}。Weizhi 只提供脚本引擎。
 * MCP 不在此注册 Java 工具：脚本 {@code $mcp} 走引擎客户端，配置在 {@code agentRoot/mcp_servers.json}。
 * Skill 加载统一走宿主注册的 {@code load_skill}（内核 {@link com.agent1.javaagent.tool.skill.LoadSkillByNameTool}）。
 */
public final class WeizhiWorkspaceTools {

    private static final java.nio.file.Path DEFAULT_WEIZHI_REPO =
        WeizhiHostSupport.defaultWeizhiRepo();

    private WeizhiWorkspaceTools() {
    }

    /** 供 {@link com.agent1.javaagent.session.ProductivityAgentHost} 装配：含 MCP 配置目录与项目级 skill。 */
    public static WorkspaceToolProvider provider(Path agentRoot, Path projectRoot) {
        Path mcpBase = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
        Path skillsProject = projectRoot == null
            ? null
            : projectRoot.toAbsolutePath().normalize();
        return sandbox -> create(sandbox, mcpBase, skillsProject);
    }

    public static List<AgentTool> create(WorkspaceSandbox sandbox) {
        return create(sandbox, null, null);
    }

    public static List<AgentTool> create(
        WorkspaceSandbox sandbox,
        Path agentRootForMcp,
        Path projectRootForSkills
    ) {
        if (sandbox == null) {
            throw new IllegalArgumentException("sandbox required");
        }
        List<AgentTool> tools = new ArrayList<>();
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.GrepTool(sandbox)));
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.GlobTool(sandbox)));
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.ZipTools(sandbox)));
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.BashTool(sandbox)));
        if (CdpWebViewRuntime.isAvailable()) {
            CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(DEFAULT_WEIZHI_REPO);
            tools.addAll(AnnotatedTools.from(new DesktopWebViewExecTool(runtime, sandbox)));
        }
        return List.copyOf(tools);
    }
}
