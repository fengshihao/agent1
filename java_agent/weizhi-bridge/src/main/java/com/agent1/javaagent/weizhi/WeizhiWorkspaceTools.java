package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.WorkspaceToolProvider;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.weizhi.agent.mcp.McpAgentExtension;
import com.weizhi.agent.skill.CompositeSkillRepository;
import com.weizhi.agent.skill.FileSystemSkillRepository;
import com.weizhi.agent.skill.LoadSkillTool;
import com.weizhi.agent.tool.AgentToolkit;
import com.weizhi.agent.tool.builtin.BashTool;
import com.weizhi.agent.tool.builtin.GlobTool;
import com.weizhi.agent.tool.builtin.GrepTool;
import java.nio.file.Path;
import java.util.List;

/**
 * 桌面生产力路径追加的 Weizhi 工具环。
 * 文件读写仍用 Agent1 的 read/write/edit/list；这里补搜索、压缩、bash、skill 与 MCP（对齐 Android Weizhi 子集，无 WebView）。
 */
public final class WeizhiWorkspaceTools {

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
        Path root = sandbox.getRoot();
        com.weizhi.agent.sandbox.WorkspaceSandbox weizhiSandbox =
            new com.weizhi.agent.sandbox.WorkspaceSandbox(root);
        AgentToolkit toolkit = new AgentToolkit();
        toolkit.registerTool(new GrepTool(weizhiSandbox));
        toolkit.registerTool(new GlobTool(weizhiSandbox));
        toolkit.registerTool(new com.weizhi.agent.tool.builtin.ZipTools(weizhiSandbox));
        toolkit.registerTool(new BashTool(weizhiSandbox));
        toolkit.registerTool(buildLoadSkillTool(root, projectRootForSkills));
        if (agentRootForMcp != null) {
            new McpAgentExtension(agentRootForMcp).register(toolkit, weizhiSandbox);
        }
        return WeizhiToolkitAdapters.toAgentTools(toolkit);
    }

    private static LoadSkillTool buildLoadSkillTool(Path workspaceRoot, Path projectRoot) {
        FileSystemSkillRepository workspaceSkills =
            new FileSystemSkillRepository(workspaceRoot.resolve("skills"));
        if (projectRoot == null) {
            return new LoadSkillTool(workspaceSkills);
        }
        Path claudeSkills = projectRoot.resolve(".claude").resolve("skills");
        FileSystemSkillRepository projectSkills = new FileSystemSkillRepository(claudeSkills);
        return new LoadSkillTool(new CompositeSkillRepository(projectSkills, workspaceSkills));
    }
}
