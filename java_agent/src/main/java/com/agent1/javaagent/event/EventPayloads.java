package com.agent1.javaagent.event;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolExecutionUpdate;
import java.util.List;

public final class EventPayloads {
    private EventPayloads() {
    }

    public static final class TurnStart {
        private final int turnIndex;

        public TurnStart(int turnIndex) {
            this.turnIndex = turnIndex;
        }

        public int getTurnIndex() {
            return turnIndex;
        }
    }

    public static final class MessageEvent {
        private final AgentMessage message;

        public MessageEvent(AgentMessage message) {
            this.message = message;
        }

        public AgentMessage getMessage() {
            return message;
        }
    }

    public static final class MessageUpdate {
        private final String delta;
        private final AgentMessage partialMessage;

        public MessageUpdate(String delta, AgentMessage partialMessage) {
            this.delta = delta;
            this.partialMessage = partialMessage;
        }

        public String getDelta() {
            return delta;
        }

        public AgentMessage getPartialMessage() {
            return partialMessage;
        }
    }

    public static final class ToolExecutionStart {
        private final ToolCall toolCall;

        public ToolExecutionStart(ToolCall toolCall) {
            this.toolCall = toolCall;
        }

        public ToolCall getToolCall() {
            return toolCall;
        }
    }

    public static final class ToolExecutionUpdatePayload {
        private final String toolCallId;
        private final ToolExecutionUpdate update;

        public ToolExecutionUpdatePayload(String toolCallId, ToolExecutionUpdate update) {
            this.toolCallId = toolCallId;
            this.update = update;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public ToolExecutionUpdate getUpdate() {
            return update;
        }
    }

    public static final class ToolExecutionEnd {
        private final String toolCallId;
        private final ToolExecutionResult result;
        private final boolean isError;
        private final String errorMessage;

        public ToolExecutionEnd(
            String toolCallId,
            ToolExecutionResult result,
            boolean isError,
            String errorMessage
        ) {
            this.toolCallId = toolCallId;
            this.result = result;
            this.isError = isError;
            this.errorMessage = errorMessage;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public ToolExecutionResult getResult() {
            return result;
        }

        public boolean isError() {
            return isError;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class TurnEnd {
        private final AgentMessage assistantMessage;
        private final List<AgentMessage> toolResults;

        public TurnEnd(AgentMessage assistantMessage, List<AgentMessage> toolResults) {
            this.assistantMessage = assistantMessage;
            this.toolResults = toolResults;
        }

        public AgentMessage getAssistantMessage() {
            return assistantMessage;
        }

        public List<AgentMessage> getToolResults() {
            return toolResults;
        }
    }

    public static final class AgentEnd {
        private final List<AgentMessage> messages;

        public AgentEnd(List<AgentMessage> messages) {
            this.messages = messages;
        }

        public List<AgentMessage> getMessages() {
            return messages;
        }
    }

    public static final class AgentError {
        private final String message;

        public AgentError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
