package com.agent1.javaagent.script;

import java.util.Map;
import java.util.Set;

/**
 * 脚本内 {@code $tools} 可调用的宿主工具。由 Weizhi {@code __caps} 的 {@code agent.tool} 转发。
 */
public interface ScriptToolBridge {

    Set<String> exposedNames();

    /**
     * @param arguments 工具参数；空调用时为空 map
     * @return 回给脚本的结果文本
     */
    String call(String toolName, Map<String, Object> arguments);
}
