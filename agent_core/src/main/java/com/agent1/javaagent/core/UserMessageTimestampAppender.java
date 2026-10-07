package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 每条用户消息末尾附加「（时间：yyyy-MM-dd HH:mm）」再发给模型。
 *
 * 时间取消息自身的 {@link AgentMessage#getTimestampMs()}（transcript 持久化为 createdAt），
 * 因此历史消息也各自携带发出时间，长会话中模型能感知时间流逝；
 * 系统提示词不再注入会过期的日期，只保留时区 / 语言。
 * 仅影响发给模型的上下文，不改动 transcript 落盘内容。
 */
public final class UserMessageTimestampAppender implements ContextTransformer {

    static final String MARK = "（时间：";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public List<AgentMessage> transform(List<AgentMessage> messages) {
        List<AgentMessage> out = new ArrayList<>(messages.size());
        for (AgentMessage message : messages) {
            out.add(isUser(message) ? withTimestamp(message) : message);
        }
        return out;
    }

    private static boolean isUser(AgentMessage message) {
        return AgentMessage.ROLE_USER.equals(message.getRole());
    }

    private static AgentMessage withTimestamp(AgentMessage message) {
        // transform 的输入始终是 state 中的原始消息（输出仅用于本次模型请求，不回写），
        // 因此这里直接追加即可，不会二次附加。
        LocalDateTime time = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(message.getTimestampMs()),
            ZoneId.systemDefault()
        );
        return message.withContent(message.getContent() + "\n\n" + MARK + time.format(FORMAT) + "）");
    }
}