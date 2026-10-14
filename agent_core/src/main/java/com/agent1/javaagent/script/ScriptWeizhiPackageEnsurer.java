package com.agent1.javaagent.script;

import com.agent1.javaagent.core.CancellationToken;

/**
 * run_js 执行前按需安装 {@code weizhi:<name>} 包（Registry ensure）。
 * 实现通常在 weizhi-bridge；未装配时 {@link com.agent1.javaagent.tool.script.ExecuteScriptTool} 跳过。
 */
@FunctionalInterface
public interface ScriptWeizhiPackageEnsurer {

    /**
     * @return {@code null} 表示成功；非空为应直接返回给模型的错误文案（勿进入 QuickJS）
     */
    String ensureForSource(String scriptSource, CancellationToken cancellationToken);
}
