package com.agent1.javaagent.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactLibraryTest {

    @TempDir
    Path temp;

    @Test
    void isArtifactRelativePathRules() {
        assertTrue(ArtifactLibrary.isArtifactRelativePath("out/report.docx"));
        assertFalse(ArtifactLibrary.isArtifactRelativePath("imports/a.png"));
        assertFalse(ArtifactLibrary.isArtifactRelativePath("tmp/x"));
        assertFalse(ArtifactLibrary.isArtifactRelativePath(".hidden/a"));
    }

    @Test
    void listsArtifactsSortedByModified() throws Exception {
        Path sessions = temp.resolve("sessions");
        Path ws = sessions.resolve("s1").resolve("workspace");
        Files.createDirectories(ws.resolve("out"));
        Path a = ws.resolve("out/a.md");
        Path b = ws.resolve("report.html");
        Files.writeString(a, "a");
        Files.writeString(b, "b");
        Files.setLastModifiedTime(a, FileTime.from(Instant.EPOCH));
        Files.setLastModifiedTime(b, FileTime.from(Instant.now()));

        var entries = ArtifactLibrary.listArtifacts(sessions);
        assertEquals(2, entries.size());
        assertEquals("report.html", entries.get(0).workspaceRelativePath());
    }
}
