package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import com.agent1.javaagent.tool.anno.AnnotatedTools;
import com.agent1.javaagent.weizhi.desktop.DesktopWebViewExecTool;
import com.agent1.javaagent.weizhi.desktop.cdp.CdpWebViewRuntime;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.weizhi.agent.skill.CompositeSkillRepository;
import com.weizhi.agent.skill.FileSystemSkillRepository;
import com.weizhi.agent.tool.AgentToolkit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 桌面生产力路径追加的 Weizhi 工具环。
 * 文件读写、grep、glob、zip、bash、skill 用 Agent1 的 {@code @Tool}；这里仍从 Weizhi 补桌面 CDP {@code webview_exec}。
 * MCP 不在此注册 Java 工具：脚本 {@code $mcp} 走引擎客户端，配置在 {@code agentRoot/mcp_servers.json}。
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
        com.weizhi.agent.sandbox.WorkspaceSandbox weizhiSandbox =
            WeizhiSandboxFactory.forProductivity(sandbox);
        AgentToolkit toolkit = new AgentToolkit();
        if (CdpWebViewRuntime.isAvailable()) {
            CdpWebViewRuntime runtime = CdpWebViewRuntime.getInstance(DEFAULT_WEIZHI_REPO);
            toolkit.registerTool(new DesktopWebViewExecTool(runtime, weizhiSandbox));
            toolkit.addJsExposed("webview_exec");
        }
        List<AgentTool> tools = new ArrayList<>();
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.GrepTool(sandbox)));
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.GlobTool(sandbox)));
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.ZipTools(sandbox)));
        tools.addAll(AnnotatedTools.from(new com.agent1.javaagent.tool.workspace.BashTool(sandbox)));
        tools.addAll(AnnotatedTools.from(buildLoadSkillTool(sandbox.getRoot(), projectRootForSkills, agentRootForMcp)));
        tools.addAll(WeizhiToolkitAdapters.toAgentTools(toolkit, sandbox));
        return List.copyOf(tools);
    }

    private static com.agent1.javaagent.tool.skill.LoadSkillTool buildLoadSkillTool(
        Path workspaceRoot,
        Path projectRoot,
        Path agentRoot
    ) {
        FileSystemSkillRepository workspaceSkills =
            new FileSystemSkillRepository(workspaceRoot.resolve("skills"));
        if (projectRoot == null && agentRoot == null) {
            return new com.agent1.javaagent.tool.skill.LoadSkillTool(workspaceSkills::readResource);
        }
        java.util.List<com.weizhi.agent.skill.SkillRepository> repos = new java.util.ArrayList<>();
        if (projectRoot != null) {
            Path claudeSkills = projectRoot.resolve(".claude").resolve("skills");
            repos.add(new FileSystemSkillRepository(claudeSkills));
        }
        repos.add(workspaceSkills);
        if (agentRoot != null) {
            Path root = agentRoot.toAbsolutePath().normalize();
            repos.add(new FileSystemSkillRepository(root.resolve("shared/catalog/skills")));
            repos.add(new FileSystemSkillRepository(root.resolve("shared/local/skills")));
        }
        com.weizhi.agent.skill.SkillRepository repo =
            new CompositeSkillRepository(repos.toArray(new com.weizhi.agent.skill.SkillRepository[0]));
        return new com.agent1.javaagent.tool.skill.LoadSkillTool(repo::readResource);
    }
}
