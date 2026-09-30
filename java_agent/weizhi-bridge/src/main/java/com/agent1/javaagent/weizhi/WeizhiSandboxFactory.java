package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.agent.AgentDocReadMounts;
import com.agent1.javaagent.agent.AgentReadMount;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.weizhi.agent.sandbox.ReadMount;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 将 Agent1 工作区沙箱映射为 Weizhi {@code WorkspaceSandbox}（含文档只读挂载）。 */
public final class WeizhiSandboxFactory {

    private WeizhiSandboxFactory() {
    }

    public static com.weizhi.agent.sandbox.WorkspaceSandbox forProductivity(WorkspaceSandbox agent1Sandbox) {
        if (agent1Sandbox == null) {
            throw new IllegalArgumentException("sandbox required");
        }
        Path writeRoot = agent1Sandbox.getRoot();
        Path agentRoot = agent1Sandbox.agentRoot();
        List<ReadMount> mounts = new ArrayList<>();
        if (agentRoot != null) {
            for (AgentReadMount m : AgentDocReadMounts.forAgentRoot(agentRoot)) {
                mounts.add(new ReadMount(m.logicalPrefix(), m.root()));
            }
        }
        return new com.weizhi.agent.sandbox.WorkspaceSandbox(writeRoot, mounts);
    }
}
