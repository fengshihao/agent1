package com.agent1.javaagent.mcp;

/** 脚本里调用 MCP 工具的写法。合法标识符用点号，其余用括号。 */
public final class McpCallForm {

    private McpCallForm() {
    }

    public static String entry(String server, String tool) {
        if (isIdent(server) && isIdent(tool)) {
            return "$mcp." + server + "." + tool;
        }
        return "$mcp[" + jsString(server) + "][" + jsString(tool) + "]";
    }

    private static boolean isIdent(String value) {
        return value != null && value.matches("[A-Za-z_$][A-Za-z0-9_$]*");
    }

    private static String jsString(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
