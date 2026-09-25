package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.script.ScriptToolBridge;
import com.weizhi.WeizhiEngine;
import com.weizhi.agent.script.ScriptToolsBridge;
import com.weizhi.platform.MiniJson;
import java.util.Map;
import java.util.Set;

/** 在 caps 安装之后链式挂上 {@code $tools}，并把用户脚本包进 Weizhi 官方 prelude。 */
public final class WeizhiScriptToolInstaller {

    private WeizhiScriptToolInstaller() {
    }

    public static void install(WeizhiEngine engine, ScriptToolBridge bridge) {
        if (engine == null || bridge == null) {
            return;
        }
        WeizhiEngine.HostCall inner = engine.getHostCall();
        engine.setHostCall(argsJson -> dispatch(argsJson, bridge, inner));
    }

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
        return prelude() + "\n" + source;
    }

    /** 与 {@code ScriptToolsBridge} prelude 一致，供顶层 {@code return await $tools...} 使用。 */
    private static String prelude() {
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
            + "})();";
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
