package com.agent1.javaagent.llm.scripted;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.llm.LlmStreamListener;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.tool.AgentTool;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 测试 / 离线自动化用 Mock LLM：按「用户任务」、**上一轮 tool 回执**或固定轮次队列返回预定
 * {@link AssistantResponse}，不访问真实大模型 API。
 */
public final class ScriptedLlmClient implements LlmClient {

    private final Deque<AssistantResponse> globalTurns;
    private final List<TaskScript> taskScripts;
    private final List<ToolResultScript> toolResultScripts;

    private ScriptedLlmClient(
        Deque<AssistantResponse> globalTurns,
        List<TaskScript> taskScripts,
        List<ToolResultScript> toolResultScripts
    ) {
        this.globalTurns = globalTurns;
        this.taskScripts = List.copyOf(taskScripts);
        this.toolResultScripts = List.copyOf(toolResultScripts);
    }

    /** 按调用顺序依次 dequeue（适合工具循环：tool call → 最终文本）。 */
    public static ScriptedLlmClient sequence(AssistantResponse... turns) {
        Deque<AssistantResponse> q = new ArrayDeque<>();
        for (AssistantResponse turn : turns) {
            q.add(Objects.requireNonNull(turn, "turn"));
        }
        return new ScriptedLlmClient(q, List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AssistantResponse streamChat(
        ChatRequest request,
        List<AgentTool> tools,
        LlmStreamListener streamListener,
        CancellationToken cancellationToken
    ) {
        AssistantResponse response = resolveNext(request);
        if (!response.getContent().isBlank()) {
            streamListener.onTextDelta(response.getContent());
        }
        return response;
    }

    private AssistantResponse resolveNext(ChatRequest request) {
        AgentMessage last = lastMessage(request);
        if (last != null && AgentMessage.ROLE_TOOL_RESULT.equals(last.getRole())) {
            ToolResultView view = new ToolResultView(last.getContent(), last.isError());
            for (ToolResultScript script : toolResultScripts) {
                if (script.matches(view) && !script.turns.isEmpty()) {
                    return script.turns.removeFirst();
                }
            }
        }

        String userText = lastUserText(request);
        if (userText != null) {
            for (TaskScript script : taskScripts) {
                if (script.matches(userText) && !script.turns.isEmpty()) {
                    return script.turns.removeFirst();
                }
            }
        }

        AssistantResponse next = globalTurns.pollFirst();
        if (next == null) {
            throw new IllegalStateException(
                "ScriptedLlmClient 无更多预定回复（lastRole="
                    + (last == null ? "?" : last.getRole())
                    + " user="
                    + (userText == null ? "?" : userText)
                    + "）"
            );
        }
        return next;
    }

    private static AgentMessage lastMessage(ChatRequest request) {
        List<AgentMessage> messages = request.getMessages();
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    private static String lastUserText(ChatRequest request) {
        List<AgentMessage> messages = request.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage m = messages.get(i);
            if (AgentMessage.ROLE_USER.equals(m.getRole())) {
                return m.getContent();
            }
        }
        return null;
    }

    public static final class Builder {
        private final Deque<AssistantResponse> globalTurns = new ArrayDeque<>();
        private final List<TaskScript> taskScripts = new ArrayList<>();
        private final List<ToolResultScript> toolResultScripts = new ArrayList<>();

        public Builder then(AssistantResponse... turns) {
            for (AssistantResponse turn : turns) {
                globalTurns.add(Objects.requireNonNull(turn, "turn"));
            }
            return this;
        }

        public Builder whenUserMessageContains(String needle, AssistantResponse... turns) {
            return whenUserMessage(
                text -> text != null && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)),
                turns
            );
        }

        public Builder whenUserMessage(Predicate<String> matcher, AssistantResponse... turns) {
            Deque<AssistantResponse> q = new ArrayDeque<>();
            for (AssistantResponse turn : turns) {
                q.add(Objects.requireNonNull(turn, "turn"));
            }
            taskScripts.add(new TaskScript(matcher, q));
            return this;
        }

        /** 上一轮为 toolResult 且内容包含片段时（优先于用户任务队列的后续轮次）。 */
        public Builder whenToolResultContains(String needle, AssistantResponse... turns) {
            return whenToolResult(view -> view.contentContains(needle), turns);
        }

        /** 上一轮 tool 标记 error 或 JSON {@code ok:false}。 */
        public Builder whenToolResultFailed(AssistantResponse... turns) {
            return whenToolResult(ToolResultView::looksLikeFailure, turns);
        }

        /** 上一轮 tool 回执不像失败（窄定义，见 {@link ToolResultView#looksLikeFailure()}）。 */
        public Builder whenToolResultSucceeded(AssistantResponse... turns) {
            return whenToolResult(view -> !view.looksLikeFailure(), turns);
        }

        public Builder whenToolResult(Predicate<ToolResultView> matcher, AssistantResponse... turns) {
            Deque<AssistantResponse> q = new ArrayDeque<>();
            for (AssistantResponse turn : turns) {
                q.add(Objects.requireNonNull(turn, "turn"));
            }
            toolResultScripts.add(new ToolResultScript(matcher, q));
            return this;
        }

        public ScriptedLlmClient build() {
            return new ScriptedLlmClient(globalTurns, taskScripts, toolResultScripts);
        }
    }

    private static final class TaskScript {
        private final Predicate<String> matcher;
        private final Deque<AssistantResponse> turns;

        private TaskScript(Predicate<String> matcher, Deque<AssistantResponse> turns) {
            this.matcher = matcher;
            this.turns = turns;
        }

        boolean matches(String userText) {
            return matcher.test(userText);
        }
    }

    private static final class ToolResultScript {
        private final Predicate<ToolResultView> matcher;
        private final Deque<AssistantResponse> turns;

        private ToolResultScript(Predicate<ToolResultView> matcher, Deque<AssistantResponse> turns) {
            this.matcher = matcher;
            this.turns = turns;
        }

        boolean matches(ToolResultView view) {
            return matcher.test(view);
        }
    }
}
