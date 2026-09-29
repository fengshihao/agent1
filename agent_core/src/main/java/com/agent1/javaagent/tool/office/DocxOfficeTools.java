package com.agent1.javaagent.tool.office;

import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 注册 Weizhi docx.js 相关生产力工具。 */
public final class DocxOfficeTools {

    private DocxOfficeTools() {
    }

    public static List<AgentTool> create(
        WorkspaceSandbox sandbox,
        ScriptEngineFactory engineFactory,
        long timeoutMs,
        Path agentRoot
    ) {
        List<AgentTool> tools = new ArrayList<>();
        tools.add(new DocxMarkdownToWordTool(sandbox, engineFactory, timeoutMs, agentRoot));
        tools.add(new DocxInspectTool(sandbox, engineFactory, timeoutMs, agentRoot));
        tools.add(new DocxReadGrepEditTool(sandbox, engineFactory, timeoutMs, agentRoot));
        tools.add(new DocxRawEditTool(sandbox, engineFactory, timeoutMs, agentRoot));
        return tools;
    }
}
