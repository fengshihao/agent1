package com.agent1.javaagent.tool;

import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.util.List;

/** 按会话工作区追加工具（Weizhi 工具环等）。 */
@FunctionalInterface
public interface WorkspaceToolProvider {

    List<AgentTool> toolsFor(WorkspaceSandbox sandbox);
}
