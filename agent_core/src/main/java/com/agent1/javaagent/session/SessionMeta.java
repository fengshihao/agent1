package com.agent1.javaagent.session;

import java.util.Objects;

/** 会话元数据（落盘 meta.json）。 */
public final class SessionMeta {

    private final String sessionId;
    private final String title;
    private final String createdAt;
    private final String updatedAt;

    public SessionMeta(String sessionId, String title, String createdAt, String updatedAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.title = title == null ? "" : title;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTitle() {
        return title;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public SessionMeta withTitle(String newTitle) {
        return new SessionMeta(sessionId, newTitle, createdAt, updatedAt);
    }

    public SessionMeta withUpdatedAt(String newUpdatedAt) {
        return new SessionMeta(sessionId, title, createdAt, newUpdatedAt);
    }
}
