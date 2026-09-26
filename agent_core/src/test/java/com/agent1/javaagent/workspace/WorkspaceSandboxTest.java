package com.agent1.javaagent.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSandboxTest {

    @TempDir
    Path temp;

    private WorkspaceSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new WorkspaceSandbox(temp.resolve("workspace"));
    }

    @Test
    void resolveRelativePathUnderRoot() {
        Path resolved = sandbox.resolve("artifacts/out.txt");
        assertEquals(sandbox.getRoot().resolve("artifacts/out.txt").normalize(), resolved);
        assertEquals("artifacts/out.txt", sandbox.relativize(resolved));
    }

    @Test
    void parentSegmentEscapeRejected() {
        assertThrows(SecurityException.class, () -> sandbox.resolve("../outside.txt"));
    }

    @Test
    void absolutePathRejected() {
        assertThrows(SecurityException.class, () -> sandbox.resolve("/etc/passwd"));
    }

    @Test
    void siblingPrefixEscapeRejected() {
        Path root = temp.resolve("foo");
        WorkspaceSandbox tight = new WorkspaceSandbox(root);
        Path sibling = temp.resolve("foobar").resolve("secret.txt");
        assertThrows(SecurityException.class, () -> tight.relativize(sibling));
    }
}
