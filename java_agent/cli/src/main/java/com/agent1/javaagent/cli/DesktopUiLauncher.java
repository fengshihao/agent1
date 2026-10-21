package com.agent1.javaagent.cli;

import com.agent1.javaagent.cli.ui.ProductivityUiCommand;
import com.agent1.javaagent.log.AgentDataPaths;
import java.io.IOException;
import java.nio.file.Path;

/** 桌面 UI 发行包入口：不依赖 JLine，避免 REPL 专用库进 zip。 */
public final class DesktopUiLauncher {

    private DesktopUiLauncher() {
    }

    public static void main(String[] args) throws IOException {
        Path agentRoot = AgentDataPaths.agentRoot();
        int code = ProductivityUiCommand.run(agentRoot, args);
        System.exit(code);
    }
}
