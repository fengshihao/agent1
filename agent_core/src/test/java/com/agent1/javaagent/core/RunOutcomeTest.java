package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunOutcomeTest {

    @Test
    void newFailureRecordCarriesErrorFlag() {
        AgentMessage message = AgentMessage.runFailure(RunOutcome.RUN_FAILURE_PREFIX + "SSE failed");
        assertTrue(RunOutcome.isRunFailureRecord(message));
        assertTrue(RunOutcome.isRunFailureRecord(message.getContent()));
    }

    @Test
    void legacyFailureRecordMatchedByPrefixOnly() {
        AgentMessage legacy = AgentMessage.assistant("错误: DashScope error [1113]: 余额不足", List.of());
        assertTrue(RunOutcome.isRunFailureRecord(legacy));
        assertFalse(legacy.isError(), "旧格式没有 error 标志，靠前缀识别");
    }

    @Test
    void normalMessagesAreNotFailureRecords() {
        assertFalse(RunOutcome.isRunFailureRecord(AgentMessage.assistant("你好", List.of())));
        assertFalse(RunOutcome.isRunFailureRecord(AgentMessage.assistant("错误提示：请检查参数", List.of())));
        assertFalse(RunOutcome.isRunFailureRecord(AgentMessage.user("错误: 用户自己输入的文本")));
        assertFalse(RunOutcome.isRunFailureRecord(AgentMessage.toolResult("c1", "错误: 工具结果", true)));
    }

    @Test
    void assistantWithToolCallsIsNotFailureRecord() {
        AgentMessage withCalls = new AgentMessage(
            AgentMessage.ROLE_ASSISTANT,
            "错误: 伴随工具调用的中断",
            System.currentTimeMillis(),
            null,
            true,
            List.of(new ToolCall("c1", "write_file", "{}"))
        );
        assertFalse(RunOutcome.isRunFailureRecord(withCalls));
    }
}
