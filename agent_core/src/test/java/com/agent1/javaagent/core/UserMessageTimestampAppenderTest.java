package com.agent1.javaagent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserMessageTimestampAppenderTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Test
    void appendsTimestampToUserMessagesUsingTheirOwnClock() {
        long first = Instant.parse("2026-10-07T05:58:00Z").toEpochMilli();
        long second = Instant.parse("2026-10-07T12:30:00Z").toEpochMilli();
        List<AgentMessage> input = List.of(
            new AgentMessage(AgentMessage.ROLE_USER, "第一句", first, null, false, List.of()),
            AgentMessage.assistant("回复", List.of()),
            new AgentMessage(AgentMessage.ROLE_USER, "第二句", second, null, false, List.of())
        );

        List<AgentMessage> out = new UserMessageTimestampAppender().transform(input);

        assertEquals(
            "第一句\n\n（时间：" + LocalDateTime.ofInstant(Instant.ofEpochMilli(first), ZoneId.systemDefault()).format(FORMAT) + "）",
            out.get(0).getContent()
        );
        assertEquals("回复", out.get(1).getContent());
        assertEquals(
            "第二句\n\n（时间：" + LocalDateTime.ofInstant(Instant.ofEpochMilli(second), ZoneId.systemDefault()).format(FORMAT) + "）",
            out.get(2).getContent()
        );
    }

    @Test
    void keepsOriginalMessagesUntouchedAndToolResultsUnchanged() {
        AgentMessage user = AgentMessage.user("帮我整理文件");
        AgentMessage tool = AgentMessage.toolResult("t1", "结果", false);
        List<AgentMessage> out = new UserMessageTimestampAppender().transform(List.of(user, tool));

        assertEquals("帮我整理文件", user.getContent());
        assertEquals("结果", out.get(1).getContent());
        assertNotEquals(user, out.get(0));
        assertTrue(out.get(0).getContent().startsWith("帮我整理文件\n\n（时间："));
    }

    @Test
    void preservesRoleAndTimestampOfTransformedMessage() {
        long ts = 1_700_000_000_000L;
        AgentMessage user = new AgentMessage(AgentMessage.ROLE_USER, "hi", ts, null, false, List.of());
        AgentMessage out = new UserMessageTimestampAppender().transform(List.of(user)).get(0);

        assertEquals(AgentMessage.ROLE_USER, out.getRole());
        assertEquals(ts, out.getTimestampMs());
    }
}