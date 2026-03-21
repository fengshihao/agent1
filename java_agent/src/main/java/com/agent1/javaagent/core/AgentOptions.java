package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.tool.AgentTool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AgentOptions {
    private final String systemPrompt;
    private final String model;
    private final List<AgentTool> tools;
    private final List<AgentMessage> messages;
    private final ContextTransformer transformContext;
    private final Duration defaultToolTimeout;
    private final int maxContextMessages;

    private AgentOptions(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.model = Objects.requireNonNull(builder.model, "model");
        this.tools = List.copyOf(builder.tools);
        this.messages = List.copyOf(builder.messages);
        this.transformContext = builder.transformContext;
        this.defaultToolTimeout = builder.defaultToolTimeout;
        this.maxContextMessages = builder.maxContextMessages;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getModel() {
        return model;
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public ContextTransformer getTransformContext() {
        return transformContext;
    }

    public Duration getDefaultToolTimeout() {
        return defaultToolTimeout;
    }

    /**
     * Max number of user/assistant/tool messages passed to the LLM per request; 0 = unlimited.
     * Does not trim persisted {@link AgentState} history.
     */
    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public static Builder builder(String model) {
        return new Builder(model);
    }

    public static final class Builder {
        private String systemPrompt = "";
        private final String model;
        private final List<AgentTool> tools = new ArrayList<>();
        private final List<AgentMessage> messages = new ArrayList<>();
        private ContextTransformer transformContext = in -> in;
        private Duration defaultToolTimeout = Duration.ofSeconds(30);
        private int maxContextMessages;

        private Builder(String model) {
            this.model = model;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
            return this;
        }

        public Builder tools(List<AgentTool> tools) {
            this.tools.clear();
            if (tools != null) {
                this.tools.addAll(tools);
            }
            return this;
        }

        public Builder messages(List<AgentMessage> messages) {
            this.messages.clear();
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return this;
        }

        public Builder transformContext(ContextTransformer transformContext) {
            this.transformContext = transformContext == null ? (in -> in) : transformContext;
            return this;
        }

        public Builder defaultToolTimeout(Duration defaultToolTimeout) {
            if (defaultToolTimeout == null || defaultToolTimeout.isZero() || defaultToolTimeout.isNegative()) {
                return this;
            }
            this.defaultToolTimeout = defaultToolTimeout;
            return this;
        }

        /**
         * @param maxContextMessages {@code <= 0} keeps full history in LLM context (default).
         */
        public Builder maxContextMessages(int maxContextMessages) {
            this.maxContextMessages = maxContextMessages;
            return this;
        }

        public AgentOptions build() {
            return new AgentOptions(this);
        }
    }
}
