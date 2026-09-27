package com.agent1.javaagent.script;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** 测试用假引擎：记录工作区并返回固定 JSON 文本。 */
public final class FakeScriptEngineFactory implements ScriptEngineFactory {

    private final String jsonResult;
    private final RuntimeException failure;
    private final List<Path> openedWorkspaces = new ArrayList<>();
    private final AtomicReference<Path> lastEvalWorkspace = new AtomicReference<>();

    public FakeScriptEngineFactory(String jsonResult) {
        this(jsonResult, null);
    }

    /** 每次 eval 抛错（用于 UC-05 script.fail_repeat 等）。 */
    public FakeScriptEngineFactory(RuntimeException failure) {
        this(null, failure);
    }

    private FakeScriptEngineFactory(String jsonResult, RuntimeException failure) {
        this.jsonResult = jsonResult == null ? "null" : jsonResult;
        this.failure = failure;
    }

    public List<Path> openedWorkspaces() {
        return List.copyOf(openedWorkspaces);
    }

    public Path lastEvalWorkspace() {
        return lastEvalWorkspace.get();
    }

    @Override
    public ScriptEngine open(Path workspace) {
        openedWorkspaces.add(workspace);
        return new ScriptEngine() {
            @Override
            public String eval(String jsSource, long timeoutMs, com.agent1.javaagent.core.CancellationToken token) {
                lastEvalWorkspace.set(workspace);
                if (token != null && token.isCancelled()) {
                    throw new RuntimeException("cancelled");
                }
                if (failure != null) {
                    throw failure;
                }
                return jsonResult;
            }

            @Override
            public void close() {
            }
        };
    }
}
