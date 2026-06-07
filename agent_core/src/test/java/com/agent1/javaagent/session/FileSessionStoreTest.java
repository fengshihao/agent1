package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSessionStoreTest {

    @TempDir
    Path temp;

    @Test
    void twoSessionsDoNotShareTranscript() {
        FileSessionStore store = new FileSessionStore(temp);
        SessionMeta a = store.createSession();
        SessionMeta b = store.createSession();
        store.appendMessage(a.getSessionId(), "run-a", AgentMessage.user("hello A"));
        store.appendMessage(b.getSessionId(), "run-b", AgentMessage.user("hello B"));

        assertEquals(1, store.loadTranscript(a.getSessionId()).size());
        assertEquals("hello A", store.loadTranscript(a.getSessionId()).get(0).getContent());
        assertEquals("hello B", store.loadTranscript(b.getSessionId()).get(0).getContent());
    }

    @Test
    void firstUserMessageSetsTitle() {
        FileSessionStore store = new FileSessionStore(temp);
        SessionMeta s = store.createSession();
        store.appendMessage(s.getSessionId(), "r1", AgentMessage.user("计划一次东京旅行"));
        SessionMeta updated = store.getSession(s.getSessionId());
        assertEquals("计划一次东京旅行", updated.getTitle());
    }

    @Test
    void deleteSessionRemovesDirectory() throws Exception {
        FileSessionStore store = new FileSessionStore(temp);
        SessionMeta s = store.createSession();
        Path dir = store.sessionDir(s.getSessionId());
        assertTrue(Files.isDirectory(dir));
        store.deleteSession(s.getSessionId());
        assertFalse(Files.exists(dir));
    }

    @Test
    void createSessionPreparesWorkspaceLayout() throws Exception {
        FileSessionStore store = new FileSessionStore(temp);
        SessionMeta s = store.createSession();
        Path ws = store.workspaceDir(s.getSessionId());
        assertTrue(Files.isDirectory(ws.resolve("artifacts")));
        assertTrue(Files.isDirectory(ws.resolve(".spill")));
    }

    @Test
    void listSessionsSortedByUpdatedAt() throws Exception {
        FileSessionStore store = new FileSessionStore(temp);
        SessionMeta older = store.createSession();
        Thread.sleep(5);
        SessionMeta newer = store.createSession();
        store.appendMessage(newer.getSessionId(), "r", AgentMessage.user("ping"));
        assertEquals(newer.getSessionId(), store.listSessions().get(0).getSessionId());
        assertEquals(2, store.listSessions().size());
    }
}
