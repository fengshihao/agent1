package com.agent1.javaagent.cli;

import com.agent1.javaagent.cli.logging.JsonlLogger;
import com.agent1.javaagent.cli.skills.ClaudeSkill;
import com.agent1.javaagent.cli.skills.ClaudeSkillLoader;
import com.agent1.javaagent.cli.skills.ClaudeSkillPromptRenderer;
import com.agent1.javaagent.cli.tools.RunBashTool;
import com.agent1.javaagent.cli.tools.RunPythonTool;
import com.agent1.javaagent.core.AgentOptions;
import com.agent1.javaagent.core.AgentRuntime;
import com.agent1.javaagent.event.AgentEvent;
import com.agent1.javaagent.event.AgentEventType;
import com.agent1.javaagent.event.EventPayloads;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient;
import com.agent1.javaagent.llm.openai.OpenAiCompatibleConfig;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.tool.AgentTool;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaAgentCli {
    private static final Pattern SKILL_COMMAND_PATTERN = Pattern.compile("^/([A-Za-z0-9:_-]+)(?:\\s+(.*))?$");
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final int TOOL_TEXT_PREVIEW_LIMIT = 280;
    private static final int TOOL_ARG_PREVIEW_LIMIT = 220;

    private JavaAgentCli() {
    }

    public static void main(String[] args) throws IOException {
        String prompt = null;
        boolean noStream = false;
        for (String arg : args) {
            if ("--no-stream".equals(arg)) {
                noStream = true;
            } else if (prompt == null) {
                prompt = arg;
            }
        }

        String apiKey = firstNonBlank(System.getenv("DASHSCOPE_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (isBlank(apiKey)) {
            System.err.println("缺少 API key。请设置 DASHSCOPE_API_KEY 或 OPENAI_API_KEY");
            System.exit(1);
            return;
        }
        String baseUrl = firstNonBlank(
            System.getenv("ALIBABA_BASE_URL"),
            System.getenv("OPENAI_BASE_URL"),
            "https://dashscope.aliyuncs.com/compatible-mode/v1"
        );
        String model = firstNonBlank(System.getenv("OPENAI_MODEL"), "qwen3.5-flash");
        final boolean streamDisabled = noStream;
        final boolean enableColor = shouldEnableColor();
        final JsonlLogger logger = new JsonlLogger();
        final AtomicReference<RunContext> currentRun = new AtomicReference<>();
        final Map<String, ClaudeSkill> skillsByName = loadDiscoveredSkills(enableColor);

        List<AgentTool> tools = List.of(new RunBashTool(), new RunPythonTool());
        String systemPrompt = "你是一个有帮助的 AI 助手，可以执行 bash 命令和 Python 脚本。"
            + "当用户需要执行命令或脚本时，使用 run_bash 或 run_python 工具。"
            + "请使用 Markdown 格式回复。"
            + runtimeEnvPrompt();

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder(model)
                .systemPrompt(systemPrompt)
                .tools(tools)
                .build(),
            new OpenAiCompatibleClient(
                new OpenAiCompatibleConfig(apiKey, baseUrl, Duration.ofSeconds(120), 0.2)
            )
        );

        runtime.observeEvents().subscribe(event -> onEvent(event, streamDisabled, enableColor, logger, currentRun));

        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
        System.out.println(colorize(ANSI_DIM, enableColor, "日志文件: " + logger.getLogPath()));

        if (!isBlank(prompt)) {
            runOnce(runtime, logger, currentRun, skillsByName, prompt, streamDisabled, enableColor);
            runtime.close();
            return;
        }

        runInteractive(runtime, logger, currentRun, skillsByName, streamDisabled, enableColor);
        runtime.close();
    }

    private static void runOnce(
        AgentRuntime runtime,
        JsonlLogger logger,
        AtomicReference<RunContext> currentRun,
        Map<String, ClaudeSkill> skillsByName,
        String prompt,
        boolean noStream,
        boolean enableColor
    ) {
        executePrompt(runtime, logger, currentRun, skillsByName, prompt, "single", noStream, enableColor);
    }

    private static void runInteractive(
        AgentRuntime runtime,
        JsonlLogger logger,
        AtomicReference<RunContext> currentRun,
        Map<String, ClaudeSkill> skillsByName,
        boolean noStream,
        boolean enableColor
    ) throws IOException {
        System.out.println(colorize(ANSI_BOLD, enableColor, "Java Agent 交互模式（输入 /exit 退出）"));
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8)
        );
        while (true) {
            System.out.print("\n" + colorize(ANSI_CYAN, enableColor, "你> "));
            String input = reader.readLine();
            if (input == null) {
                break;
            }
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("/exit".equalsIgnoreCase(trimmed) || "exit".equalsIgnoreCase(trimmed)) {
                break;
            }
            executePrompt(runtime, logger, currentRun, skillsByName, trimmed, "interactive", noStream, enableColor);
        }
    }

    private static void executePrompt(
        AgentRuntime runtime,
        JsonlLogger logger,
        AtomicReference<RunContext> currentRun,
        Map<String, ClaudeSkill> skillsByName,
        String userInput,
        String mode,
        boolean noStream,
        boolean enableColor
    ) {
        ResolvedPrompt resolvedPrompt = resolvePrompt(userInput, skillsByName, enableColor);
        String prompt = resolvedPrompt.prompt();
        String runMode = resolvedPrompt.skillName() == null ? mode : mode + ":skill(" + resolvedPrompt.skillName() + ")";
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        RunContext run = new RunContext(runId, prompt, runMode);
        currentRun.set(run);

        logger.writeEvent(
            "run_started",
            runId,
            Map.of(
                "mode", runMode,
                "prompt", prompt,
                "user_input", userInput,
                "skill_name", resolvedPrompt.skillName() == null ? "" : resolvedPrompt.skillName(),
                "log_file", logger.getLogPath().toString(),
                "message_history_count", runtime.getStateSnapshot().getMessages().size()
            )
        );
        logger.writeEvent("model_request", runId, Map.of("input_prompt", prompt));
        System.out.println(colorize(ANSI_DIM, enableColor, "运行中（run_id=" + runId + "）..."));

        try {
            runtime.prompt(prompt).join();
            runtime.waitForIdle();

            AgentMessage latestAssistant = findLatestAssistantMessage(runtime);
            if (latestAssistant != null) {
                logger.writeEvent(
                    "model_response",
                    runId,
                    Map.of(
                        "output", latestAssistant.getContent(),
                        "all_messages_count", runtime.getStateSnapshot().getMessages().size()
                    )
                );
            }
            logger.writeEvent("run_completed", runId, Map.of("status", "ok"));
            System.out.println();
        } catch (Exception e) {
            logger.writeEvent("run_failed", runId, Map.of("error", String.valueOf(e)));
            System.err.println(colorize(ANSI_RED, enableColor, "[error] " + e.getMessage()));
        } finally {
            currentRun.set(null);
            if (noStream) {
                // no-op, response already printed in MESSAGE_END.
            }
        }
    }

    private static void onEvent(
        AgentEvent event,
        boolean noStream,
        boolean enableColor,
        JsonlLogger logger,
        AtomicReference<RunContext> currentRun
    ) {
        RunContext run = currentRun.get();
        if (run == null) {
            return;
        }
        if (event.getType() == AgentEventType.MESSAGE_UPDATE) {
            EventPayloads.MessageUpdate payload = (EventPayloads.MessageUpdate) event.getPayload();
            if (!noStream) {
                System.out.print(payload.getDelta());
                System.out.flush();
            }
            run.sawTextDelta = true;
            logger.writeEvent("model_text_delta", run.runId, Map.of("delta", payload.getDelta()));
            return;
        }
        if (event.getType() == AgentEventType.MESSAGE_END) {
            EventPayloads.MessageEvent payload = (EventPayloads.MessageEvent) event.getPayload();
            AgentMessage message = payload.getMessage();
            if (AgentMessage.ROLE_ASSISTANT.equals(message.getRole()) && (noStream || !run.sawTextDelta)) {
                System.out.print(message.getContent());
                System.out.flush();
            }
            run.sawTextDelta = false;
            return;
        }
        if (event.getType() == AgentEventType.TOOL_EXECUTION_START) {
            EventPayloads.ToolExecutionStart payload = (EventPayloads.ToolExecutionStart) event.getPayload();
            String argsPreview = truncate(payload.getToolCall().getArgumentsJson(), TOOL_ARG_PREVIEW_LIMIT);
            System.out.println(
                "\n" + colorize(
                    ANSI_CYAN,
                    enableColor,
                    "[tool] 调用 " + payload.getToolCall().getName() + " args=" + argsPreview
                )
            );
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("tool_name", payload.getToolCall().getName());
            fields.put("tool_args", payload.getToolCall().getArgumentsJson());
            fields.put("tool_call_id", payload.getToolCall().getId());
            logger.writeEvent("tool_call", run.runId, fields);
            return;
        }
        if (event.getType() == AgentEventType.TOOL_EXECUTION_END) {
            EventPayloads.ToolExecutionEnd payload = (EventPayloads.ToolExecutionEnd) event.getPayload();
            String resultText = payload.getResult() == null ? "" : payload.getResult().getText();
            String preview = truncate(resultText, TOOL_TEXT_PREVIEW_LIMIT);
            if (payload.isError()) {
                System.out.println(colorize(
                    ANSI_RED,
                    enableColor,
                    "[tool] 失败 " + payload.getToolCallId() + ": " + truncate(payload.getErrorMessage(), TOOL_TEXT_PREVIEW_LIMIT)
                ));
            } else {
                System.out.println(colorize(
                    ANSI_GREEN,
                    enableColor,
                    "[tool] 完成 " + payload.getToolCallId() + " result=" + preview
                ));
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("tool_call_id", payload.getToolCallId());
            fields.put("result", resultText);
            fields.put("is_error", payload.isError());
            if (payload.isError()) {
                fields.put("error_message", payload.getErrorMessage());
            }
            logger.writeEvent("tool_result", run.runId, fields);
            return;
        }
        if (event.getType() == AgentEventType.AGENT_ERROR) {
            EventPayloads.AgentError payload = (EventPayloads.AgentError) event.getPayload();
            logger.writeEvent("run_failed", run.runId, Map.of("error", payload.getMessage()));
            System.err.println("\n" + colorize(ANSI_RED, enableColor, "[error] " + payload.getMessage()));
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static AgentMessage findLatestAssistantMessage(AgentRuntime runtime) {
        List<AgentMessage> messages = runtime.getStateSnapshot().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (AgentMessage.ROLE_ASSISTANT.equals(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...(truncated)";
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

    private static String runtimeEnvPrompt() {
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String shellHint = firstNonBlank(System.getenv("SHELL"), System.getenv("ComSpec"), "unknown");
        String cwd = Path.of(".").toAbsolutePath().normalize().toString();

        String shellPreference;
        String osLower = osName.toLowerCase(Locale.ROOT);
        if (osLower.contains("mac")) {
            shellPreference = "当前是 macOS，优先使用 zsh 语法命令。";
        } else if (osLower.contains("win")) {
            shellPreference = "当前是 Windows，优先 bash；不可用时回退 PowerShell 或 cmd。";
        } else {
            shellPreference = "当前是 Linux/Unix，优先使用 bash 命令。";
        }

        return "当前运行环境信息如下，请据此选择命令和代码写法："
            + "OS=" + osName + " " + osVersion
            + "，Shell=" + shellHint
            + "，CWD=" + cwd + "。"
            + shellPreference;
    }

    private static Map<String, ClaudeSkill> loadDiscoveredSkills(boolean enableColor) {
        ClaudeSkillLoader loader = new ClaudeSkillLoader();
        ClaudeSkillLoader.SkillLoadResult loadResult = loader.loadFromProjectRoot(Path.of(".").toAbsolutePath().normalize());
        Map<String, ClaudeSkill> skillsByName = new LinkedHashMap<>();
        for (ClaudeSkill skill : loadResult.skills()) {
            skillsByName.put(skill.getName(), skill);
        }
        int count = loadResult.skills().size();

        if (count > 0) {
            String names = loadResult.skills().stream()
                .map(skill -> skill.getName())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            System.out.println(colorize(ANSI_DIM, enableColor, "发现 Claude skills: " + count + " 个 [" + names + "]"));
        } else {
            System.out.println(colorize(ANSI_DIM, enableColor, "发现 Claude skills: 0 个"));
        }

        for (String warning : loadResult.warnings()) {
            System.out.println(colorize(ANSI_YELLOW, enableColor, "[skills] " + warning));
        }
        return skillsByName;
    }

    private static ResolvedPrompt resolvePrompt(String userInput, Map<String, ClaudeSkill> skillsByName, boolean enableColor) {
        String trimmed = userInput == null ? "" : userInput.trim();
        if (!trimmed.startsWith("/")) {
            return new ResolvedPrompt(userInput, null);
        }

        Matcher matcher = SKILL_COMMAND_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return new ResolvedPrompt(userInput, null);
        }

        String command = matcher.group(1);
        String arguments = matcher.group(2) == null ? "" : matcher.group(2);
        ClaudeSkill skill = skillsByName.get(command);
        if (skill == null) {
            System.out.println(colorize(ANSI_YELLOW, enableColor, "[skill] 未找到: " + command + "，按普通消息处理"));
            return new ResolvedPrompt(userInput, null);
        }

        String rendered = ClaudeSkillPromptRenderer.render(skill, arguments);
        System.out.println(colorize(ANSI_CYAN, enableColor, "[skill] 使用 " + command));
        return new ResolvedPrompt(rendered, command);
    }

    private static final class RunContext {
        private final String runId;
        @SuppressWarnings("unused")
        private final String prompt;
        @SuppressWarnings("unused")
        private final String mode;
        private volatile boolean sawTextDelta;

        private RunContext(String runId, String prompt, String mode) {
            this.runId = runId;
            this.prompt = prompt;
            this.mode = mode;
            this.sawTextDelta = false;
        }
    }

    private record ResolvedPrompt(String prompt, String skillName) {
    }
}
