package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
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
 * 文件读写仍用 Agent1 的 read/write/edit/list；这里只补 Weizhi 新增的搜索、压缩、bash 与 skill。
 */
public final class WeizhiWorkspaceTools {

    private WeizhiWorkspaceTools() {
    }

    public static List<AgentTool> create(WorkspaceSandbox sandbox) {
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
        toolkit.registerTool(new LoadSkillTool(new FileSystemSkillRepository(root.resolve("skills"))));
        return WeizhiToolkitAdapters.toAgentTools(toolkit);
    }
}
