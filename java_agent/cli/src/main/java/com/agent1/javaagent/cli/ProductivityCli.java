package com.agent1.javaagent.cli;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.session.ProductivityAgentHost;
import com.agent1.javaagent.session.SessionMeta;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** 生产力助手 REPL：多会话 + Run 落盘（{@code .agent1} 数据目录）。 */
public final class ProductivityCli {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_RED = "\u001B[31m";

    private ProductivityCli() {
    }

    public static void main(String[] args) throws IOException {
        AgentRuntimeConfig runtimeConfig = AgentRuntimeConfig.fromEnvironment();
        String configError = runtimeConfig.configurationError();
        if (configError != null) {
            System.err.println(configError);
            System.exit(1);
            return;
        }

        Path agentRoot = Path.of(".").toAbsolutePath().normalize().resolve(".agent1");
        boolean enableColor = shouldEnableColor();

        try (ProductivityAgentHost host = new ProductivityAgentHost(agentRoot, runtimeConfig)) {
            ensureActiveSession(host);
            host.runtime().observeEvents().subscribe(event -> onEvent(event, enableColor));

            Runtime.getRuntime().addShutdownHook(new Thread(host::close));
            System.out.println(colorize(ANSI_DIM, enableColor, "Agent 数据目录: " + agentRoot));
            printHelp(enableColor);

            if (args.length > 0) {
                runOnce(host, String.join(" ", args), enableColor);
                return;
            }

            runRepl(host, enableColor);
        }
    }

    private static void ensureActiveSession(ProductivityAgentHost host) {
        if (host.getActiveSessionId() != null) {
            return;
        }
        List<SessionMeta> sessions = host.listSessions();
        if (sessions.isEmpty()) {
            SessionMeta created = host.createSession();
            System.out.println("已创建会话 " + created.getSessionId());
        } else {
            host.switchSession(sessions.get(0).getSessionId());
            System.out.println("已加载会话 " + sessions.get(0).getSessionId()
                + "（" + sessions.get(0).getTitle() + "）");
        }
    }

    private static void runOnce(ProductivityAgentHost host, String text, boolean enableColor) {
        try {
            String runId = host.runUserMessage(text);
            System.out.println(colorize(ANSI_DIM, enableColor, "\nrun_id=" + runId));
        } catch (Exception e) {
            System.err.println(colorize(ANSI_RED, enableColor, "[error] " + e.getMessage()));
        }
    }

    private static void runRepl(ProductivityAgentHost host, boolean enableColor) throws IOException {
        System.out.println(colorize(ANSI_BOLD, enableColor, "生产力助手（/quit 退出）"));
        String promptText = "\n" + colorize(ANSI_CYAN, enableColor, "你> ");
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();
            while (true) {
                final String input;
                try {
                    input = lineReader.readLine(promptText);
                } catch (UserInterruptException | EndOfFileException e) {
                    break;
                }
                String trimmed = input == null ? "" : input.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (handleCommand(host, trimmed, enableColor)) {
                    break;
                }
            }
        }
    }

    /** @return true 表示退出 REPL */
    private static boolean handleCommand(ProductivityAgentHost host, String trimmed, boolean enableColor) {
        if ("/quit".equalsIgnoreCase(trimmed) || "/exit".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("/new".equalsIgnoreCase(trimmed)) {
            SessionMeta meta = host.createSession();
            System.out.println("新会话 " + meta.getSessionId());
            return false;
        }
        if ("/list".equalsIgnoreCase(trimmed)) {
            for (SessionMeta meta : host.listSessions()) {
                String mark = meta.getSessionId().equals(host.getActiveSessionId()) ? "*" : " ";
                System.out.println(mark + " " + meta.getSessionId() + "  " + meta.getTitle());
            }
            return false;
        }
        if (trimmed.toLowerCase().startsWith("/use ")) {
            String id = trimmed.substring(5).trim();
            if (id.isEmpty()) {
                System.out.println("用法: /use <sessionId>");
                return false;
            }
            host.switchSession(id);
            System.out.println("已切换至 " + id);
            return false;
        }
        if (trimmed.startsWith("/")) {
            System.out.println("未知命令。可用: /new /list /use <id> /quit");
            return false;
        }
        runOnce(host, trimmed, enableColor);
        return false;
    }

    private static void onEvent(AgentEvent event, boolean enableColor) {
        if (event.getType() == AgentEventType.MESSAGE_UPDATE) {
            EventPayloads.MessageUpdate payload = (EventPayloads.MessageUpdate) event.getPayload();
            System.out.print(payload.getDelta());
            System.out.flush();
            return;
        }
        if (event.getType() == AgentEventType.MESSAGE_END) {
            EventPayloads.MessageEvent payload = (EventPayloads.MessageEvent) event.getPayload();
            AgentMessage message = payload.getMessage();
            if (AgentMessage.ROLE_ASSISTANT.equals(message.getRole())) {
                System.out.println();
            }
            return;
        }
        if (event.getType() == AgentEventType.AGENT_ERROR) {
            EventPayloads.AgentError payload = (EventPayloads.AgentError) event.getPayload();
            System.err.println("\n" + colorize(ANSI_RED, enableColor, "[error] " + payload.getMessage()));
        }
    }

    private static void printHelp(boolean enableColor) {
        System.out.println(colorize(ANSI_DIM, enableColor,
            "命令: /new  /list  /use <sessionId>  /quit"));
    }

    private static boolean shouldEnableColor() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null) {
            return false;
        }
        String term = System.getenv("TERM");
        return term != null && !"dumb".equalsIgnoreCase(term);
    }

    private static String colorize(String ansi, boolean enabled, String text) {
        if (!enabled) {
            return text;
        }
        return ansi + text + ANSI_RESET;
    }
}
