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
 * 测试 / 离线自动化用 Mock LLM：按「用户任务」或固定轮次队列返回预定 {@link AssistantResponse}，
 * 不访问真实大模型 API。
 */
public final class ScriptedLlmClient implements LlmClient {

    private final Deque<AssistantResponse> globalTurns;
    private final List<TaskScript> taskScripts;

    private ScriptedLlmClient(Deque<AssistantResponse> globalTurns, List<TaskScript> taskScripts) {
        this.globalTurns = globalTurns;
        this.taskScripts = List.copyOf(taskScripts);
    }

    /** 按调用顺序依次 dequeue（适合工具循环：tool call → 最终文本）。 */
    public static ScriptedLlmClient sequence(AssistantResponse... turns) {
        Deque<AssistantResponse> q = new ArrayDeque<>();
        for (AssistantResponse turn : turns) {
            q.add(Objects.requireNonNull(turn, "turn"));
        }
        return new ScriptedLlmClient(q, List.of());
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
        AssistantResponse response = resolveNext(lastUserText(request));
        if (!response.getContent().isBlank()) {
            streamListener.onTextDelta(response.getContent());
        }
        return response;
    }

    private AssistantResponse resolveNext(String userText) {
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
                "ScriptedLlmClient 无更多预定回复（user="
                    + (userText == null ? "?" : userText)
                    + "）"
            );
        }
        return next;
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

        public ScriptedLlmClient build() {
            return new ScriptedLlmClient(globalTurns, taskScripts);
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
}
