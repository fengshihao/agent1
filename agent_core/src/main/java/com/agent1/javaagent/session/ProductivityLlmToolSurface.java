package com.agent1.javaagent.session;

import com.agent1.javaagent.tool.AgentTool;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产力路径：部分工具只通过 {@code run_js} 内 {@code $tools.*} 暴露，不进入 LLM tool schema，
 * 以减少工具噪音与单轮 tool_call 次数消耗。实现仍在 {@link ProductivityAgentHost#buildTools} 全量注册。
 */
public final class ProductivityLlmToolSurface {

    private static final Set<String> SCRIPT_BRIDGE_ONLY = Set.of(
        "media_resize",
        "media_extract_frame",
        "media_probe",
        "promote_request",
        "list_sessions",
        "zip_create",
        "zip_extract"
    );

    private ProductivityLlmToolSurface() {
    }

    public static Set<String> scriptBridgeOnlyNames() {
        return Set.copyOf(SCRIPT_BRIDGE_ONLY);
    }

    public static List<AgentTool> forLlm(List<AgentTool> all) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        return all.stream()
            .filter(tool -> !SCRIPT_BRIDGE_ONLY.contains(tool.name()))
            .collect(Collectors.toList());
    }

    /** 供系统提示 / 测试：仍可通过 $tools 调用的名称。 */
    public static Set<String> hiddenFromLlmButInBridge(List<AgentTool> all) {
        Set<String> hidden = new LinkedHashSet<>();
        for (AgentTool tool : all) {
            if (SCRIPT_BRIDGE_ONLY.contains(tool.name())) {
                hidden.add(tool.name());
            }
        }
        return hidden;
    }
}
