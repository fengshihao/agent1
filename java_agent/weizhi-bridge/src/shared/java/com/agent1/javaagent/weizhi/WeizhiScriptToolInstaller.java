package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.mcp.McpServersFile;
import com.agent1.javaagent.script.ScriptToolBridge;
import com.weizhi.WeizhiEngine;
import com.weizhi.agent.script.ScriptToolsBridge;
import com.weizhi.platform.MiniJson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在 caps 安装之后链式挂上 {@code $tools}。用户脚本必须单独 eval，不能和 prelude 拼成一次。 */
public final class WeizhiScriptToolInstaller {

    /** 与 {@link #preludeStatic} 同步；Agent1 行号扣减用。 */
    public static final int TOOLS_PRELUDE_LINE_COUNT = lineCount(preludeStatic("{}"));

    private WeizhiScriptToolInstaller() {
    }

    public static int preludeLineCount(ScriptToolBridge bridge) {
        if (bridge == null) {
            return 0;
        }
        Set<String> names = bridge.exposedNames();
        if (names == null || names.isEmpty()) {
            return 0;
        }
        return TOOLS_PRELUDE_LINE_COUNT;
    }

    /** 仅 $tools / $mcp 注入 prelude；应在用户脚本前单独 eval。 */
    public static String preludeSource(ScriptToolBridge bridge) {
        return preludeSource(bridge, null);
    }

    public static String preludeSource(ScriptToolBridge bridge, Path mcpAgentRoot) {
        if (bridge == null) {
            return "";
        }
        Set<String> names = bridge.exposedNames();
        if (names == null || names.isEmpty()) {
            return "";
        }
        return preludeStatic(McpServersFile.scriptServersJson(mcpAgentRoot));
    }

    public static void install(WeizhiEngine engine, ScriptToolBridge bridge) {
        if (engine == null || bridge == null) {
            return;
        }
        WeizhiEngine.HostCall inner = engine.getHostCall();
        engine.setHostCall(argsJson -> dispatch(argsJson, bridge, inner));
    }

    /**
     * 一次 runJs 里要依次执行的源。prelude 与用户脚本分开，因为 QuickJS {@code JS_DetectModule}
     * 只看第一个 token：前面若是 {@code $tools} 的 IIFE，后面的 {@code import} 会被当成普通脚本，
     * 在花括号处报 {@code expecting '('}。
     */
    public static List<EvalStep> evalSteps(
        String userSource,
        String agentArgsPrelude,
        ScriptToolBridge bridge,
        String workspaceRelativeFile
    ) {
        return evalSteps(userSource, agentArgsPrelude, bridge, workspaceRelativeFile, null);
    }

    public static List<EvalStep> evalSteps(
        String userSource,
        String agentArgsPrelude,
        ScriptToolBridge bridge,
        String workspaceRelativeFile,
        Path mcpAgentRoot
    ) {
        List<EvalStep> steps = new ArrayList<>();
        if (agentArgsPrelude != null && !agentArgsPrelude.isBlank()) {
            steps.add(new EvalStep("<agent-args>", agentArgsPrelude));
        }
        String toolsPrelude = preludeSource(bridge, mcpAgentRoot);
        if (!toolsPrelude.isBlank()) {
            steps.add(new EvalStep("<tools-prelude>", toolsPrelude));
        }
        String filename = workspaceRelativeFile == null || workspaceRelativeFile.isBlank()
            ? "<eval>"
            : workspaceRelativeFile.trim();
        steps.add(new EvalStep(filename, userSource == null ? "" : userSource));
        return List.copyOf(steps);
    }

    /**
     * 把 prelude 和用户脚本拼成一段。含 {@code import}/{@code export} 的脚本不能走这里，用 {@link #evalSteps}。
     */
    public static String wrap(String source, ScriptToolBridge bridge) {
        if (source == null) {
            return "";
        }
        if (bridge == null) {
            return source;
        }
        Set<String> names = bridge.exposedNames();
        if (names == null || names.isEmpty()) {
            return source;
        }
        // __caps 会把参数 JSON.stringify、把返回值 JSON.parse。
        // 因此这里传对象，而不是再 stringify 一次（那会变成 JSON 字符串，宿主解析失败）。
        // 引擎脚本本身已是 async，顶层 await 可用。不要套 ScriptToolsBridge.wrapSource 的 async IIFE，
        // 否则 runJs 会把未拆开的 Promise 收成 {}。
        return preludeSource(bridge) + (preludeSource(bridge).isEmpty() ? "" : "\n") + source;
    }

    /** 一次 {@code runJs} 的文件名和源码。 */
    public record EvalStep(String filename, String source) {
    }

    /**
     * {@code $tools} 与微智 prelude 一致。{@code $mcp} 走引擎 {@code mcp.connect}，
     * 配置由 Agent1 写入 {@code mcp_servers.json}。
     */
    private static String preludeStatic(String serversJson) {
        String table = serversJson == null || serversJson.isBlank() ? "{}" : serversJson.trim();
        return "(function(){\n"
            + "globalThis.$tools = new Proxy({}, {\n"
            + "  get: function(_, name) {\n"
            + "    return async function(input) {\n"
            + "      var req = { op: '" + ScriptToolsBridge.OP + "', name: String(name), input: input || {} };\n"
            + "      var r = __caps(req);\n"
            + "      if (typeof r === 'string') { try { r = JSON.parse(r); } catch (e) {} }\n"
            + "      if (r && r.error) throw new Error(r.error);\n"
            + "      return (r && r.result !== undefined) ? r.result : r;\n"
            + "    };\n"
            + "  }\n"
            + "});\n"
            + "globalThis.$mcp = (function(){\n"
            + "  var servers = " + table + ";\n"
            + "  var clients = {};\n"
            + "  function clientFor(server) {\n"
            + "    var cfg = servers[server];\n"
            + "    if (!cfg || typeof cfg.url !== 'string') {\n"
            + "      return Promise.reject(new Error('unknown MCP server: ' + String(server)));\n"
            + "    }\n"
            + "    if (!clients[server]) {\n"
            + "      if (typeof globalThis.mcp !== 'object' || typeof globalThis.mcp.connect !== 'function') {\n"
            + "        return Promise.reject(new Error('weizhi mcp client is not available'));\n"
            + "      }\n"
            + "      clients[server] = globalThis.mcp.connect({ url: cfg.url, headers: cfg.headers || {} });\n"
            + "    }\n"
            + "    return clients[server];\n"
            + "  }\n"
            + "  return new Proxy({}, {\n"
            + "    get: function(_, server) {\n"
            + "      return new Proxy({}, {\n"
            + "        get: function(_, tool) {\n"
            + "          return async function(input) {\n"
            + "            var c = await clientFor(String(server));\n"
            + "            var r = await c.callTool(String(tool), input || {});\n"
            + "            if (r && r.isError) throw new Error(r.text || 'mcp tool error');\n"
            + "            return r && r.text !== undefined ? r.text : r;\n"
            + "          };\n"
            + "        }\n"
            + "      });\n"
            + "    }\n"
            + "  });\n"
            + "})();\n"
            + "})();";
    }

    private static int lineCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static String dispatch(String argsJson, ScriptToolBridge bridge, WeizhiEngine.HostCall inner) {
        Map<String, Object> args;
        try {
            args = MiniJson.object(argsJson);
        } catch (IllegalArgumentException e) {
            return inner == null ? MiniJson.error("unsupported: host call") : inner.call(argsJson);
        }
        if (!ScriptToolsBridge.OP.equals(MiniJson.str(args, "op"))) {
            return inner == null ? MiniJson.error("unsupported: host call") : inner.call(argsJson);
        }
        String name = MiniJson.str(args, "name");
        if (name.isEmpty()) {
            return MiniJson.error("bad argument: agent.tool: name required");
        }
        if (ScriptToolsBridge.EXCLUDED_TOOL.equals(name) || "execute_script".equals(name)) {
            return MiniJson.error("unsupported: $tools." + name + " (use the host script tool)");
        }
        Set<String> exposed = bridge.exposedNames();
        if (exposed == null || !exposed.contains(name)) {
            return MiniJson.error("unsupported: $tools." + name + " (not in js exposed whitelist)");
        }
        Object input = args.get("input");
        Map<String, Object> map = input instanceof Map ? (Map<String, Object>) input : Map.of();
        try {
            String result = bridge.call(name, map);
            return "{\"result\":" + MiniJson.quote(result == null ? "" : result) + "}";
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return MiniJson.error(message);
        }
    }
}
