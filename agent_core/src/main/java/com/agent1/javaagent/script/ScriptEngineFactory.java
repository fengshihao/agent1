package com.agent1.javaagent.script;

import java.nio.file.Path;

/** 为单次脚本执行创建引擎实例（推荐每轮 eval 新建，避免共享全局词法环境）。 */
public interface ScriptEngineFactory {

    ScriptEngine open(Path workspace);
}
