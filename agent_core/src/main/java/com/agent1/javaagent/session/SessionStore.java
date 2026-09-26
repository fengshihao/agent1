package com.agent1.javaagent.session;

import com.agent1.javaagent.model.AgentMessage;
import java.util.List;

/** 多会话持久化（01-会话）。 */
public interface SessionStore {

    /** 按 {@code updatedAt} 倒序。 */
    List<SessionMeta> listSessions();

    SessionMeta createSession();

    SessionMeta getSession(String sessionId);

    SessionMeta renameSession(String sessionId, String title);

    void deleteSession(String sessionId);

    List<AgentMessage> loadTranscript(String sessionId);

    /** 追加一条消息并刷新 {@code updatedAt}；首条用户消息可自动更新标题。 */
    void appendMessage(String sessionId, String runId, AgentMessage message);
}
