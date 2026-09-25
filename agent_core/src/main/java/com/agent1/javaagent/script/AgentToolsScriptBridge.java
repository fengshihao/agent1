package com.agent1.javaagent.script;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 把当前 Run 的 {@link AgentTool} 暴露给脚本。{@code execute_script} 不暴露，避免递归。 */
public final class AgentToolsScriptBridge implements ScriptToolBridge {

    static final String EXCLUDED_TOOL = "execute_script";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ToolUpdateListener NO_UPDATE = update -> {
        // 脚本内同步调用，不向前端推送工具进度。
    };

    private final List<AgentTool> tools;
    private final Set<String> exposedNames;

    public AgentToolsScriptBridge(List<AgentTool> tools) {
        this.tools = List.copyOf(tools);
        Set<String> names = new LinkedHashSet<>();
        for (AgentTool tool : this.tools) {
            if (!EXCLUDED_TOOL.equals(tool.name())) {
                names.add(tool.name());
            }
        }
        this.exposedNames = Set.copyOf(names);
    }

    @Override
    public Set<String> exposedNames() {
        return exposedNames;
    }

    @Override
    public String call(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank() || !exposedNames.contains(toolName)) {
            throw new IllegalArgumentException("unsupported: $tools." + toolName);
        }
        AgentTool tool = null;
        for (AgentTool candidate : tools) {
            if (toolName.equals(candidate.name())) {
                tool = candidate;
                break;
            }
        }
        if (tool == null) {
            throw new IllegalArgumentException("unsupported: $tools." + toolName);
        }
        JsonNode params = arguments == null || arguments.isEmpty()
            ? MAPPER.createObjectNode()
            : MAPPER.valueToTree(arguments);
        try {
            ToolExecutionResult result = tool.execute(toolName, params, new CancellationToken(), NO_UPDATE);
            return result.getText() == null ? "" : result.getText();
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException(message, e);
        }
    }
}
