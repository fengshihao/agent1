package com.agent1.javaagent.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.session.SessionMeta;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRunStoreTest {

    @TempDir
    Path temp;

    @Test
    void writeAndReadRoundTrip() throws Exception {
        FileSessionStore sessions = new FileSessionStore(temp);
        SessionMeta session = sessions.createSession();
        FileRunStore runs = new FileRunStore(sessions);

        RunRecord record = new RunRecord(
            "run-abc",
            session.getSessionId(),
            RunState.RUNNING,
            "2026-01-01T00:00:00Z",
            "2026-01-01T00:00:01Z",
            null
        );
        runs.write(record);

        Path file = runs.runFile(session.getSessionId(), "run-abc");
        assertTrue(Files.isRegularFile(file));
        RunRecord loaded = runs.read(session.getSessionId(), "run-abc");
        assertEquals(RunState.RUNNING, loaded.getState());
        assertEquals("run-abc", loaded.getRunId());
        assertTrue(loaded.getLastError().isEmpty());
    }

    @Test
    void terminalStatePersistsLastError() {
        FileSessionStore sessions = new FileSessionStore(temp);
        SessionMeta session = sessions.createSession();
        FileRunStore runs = new FileRunStore(sessions);

        RunRecord failed = new RunRecord(
            "r1",
            session.getSessionId(),
            RunState.FAILED,
            "t0",
            "t1",
            "boom"
        );
        runs.write(failed);
        assertEquals("boom", runs.read(session.getSessionId(), "r1").getLastError().orElse(""));
    }
}
