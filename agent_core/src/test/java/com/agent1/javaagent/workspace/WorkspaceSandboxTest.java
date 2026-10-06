package com.agent1.javaagent.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.nio.file.Files;
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
        Path resolved = sandbox.resolveWrite("artifacts/out.txt");
        assertEquals(sandbox.getRoot().resolve("artifacts/out.txt").normalize(), resolved);
        assertEquals("artifacts/out.txt", sandbox.relativize(resolved));
    }

    @Test
    void resolveReadAgentDocPath() throws Exception {
        Path agentRoot = temp.resolve("agentRoot");
        AgentHomeBootstrap.ensure(agentRoot);
        WorkspaceSandbox withAgent = new WorkspaceSandbox(temp.resolve("ws"), agentRoot);
        Path doc = withAgent.resolveRead("docs/system/directories.md");
        assertTrue(Files.isRegularFile(doc));
        assertEquals("docs/system/directories.md", withAgent.displayPath(doc));
    }

    @Test
    void writeRejectsAgentDocs() {
        Path agentRoot = temp.resolve("agentRoot2");
        AgentHomeBootstrap.ensure(agentRoot);
        WorkspaceSandbox withAgent = new WorkspaceSandbox(temp.resolve("ws2"), agentRoot);
        assertThrows(SecurityException.class, () -> withAgent.resolveWrite("docs/system/hack.md"));
    }

    @Test
    void parentSegmentEscapeRejected() {
        assertThrows(SecurityException.class, () -> sandbox.resolveWrite("../outside.txt"));
    }

    @Test
    void absolutePathRejected() {
        assertThrows(SecurityException.class, () -> sandbox.resolveWrite("/etc/passwd"));
    }

    @Test
    void siblingPrefixEscapeRejected() {
        Path root = temp.resolve("foo");
        WorkspaceSandbox tight = new WorkspaceSandbox(root);
        Path sibling = temp.resolve("foobar").resolve("secret.txt");
        assertThrows(SecurityException.class, () -> tight.relativize(sibling));
    }

    @Test
    void stripsRedundantWorkspacePrefix() {
        Path resolved = sandbox.resolveWrite("workspace/artifacts/dog.svg");
        assertEquals(sandbox.getRoot().resolve("artifacts/dog.svg").normalize(), resolved);
        assertEquals("artifacts/dog.svg", sandbox.relativize(resolved));
    }

    @Test
    void toWeizhiReadPathMapsAgentRootAbsoluteToDocsSystem() {
        Path agentRoot = temp.resolve("agentRoot3");
        AgentHomeBootstrap.ensure(agentRoot);
        WorkspaceSandbox withAgent = new WorkspaceSandbox(temp.resolve("ws3"), agentRoot);
        assertEquals("docs/system", withAgent.toWeizhiReadPath(agentRoot.toString()));
        assertEquals(
            "docs/system/directories.md",
            withAgent.toWeizhiReadPath(agentRoot.resolve("docs/system/directories.md").toString())
        );
    }

    @Test
    void toWeizhiReadPathMapsWorkspaceAbsolute() {
        Path file = sandbox.getRoot().resolve("notes/a.txt");
        assertEquals("notes/a.txt", sandbox.toWeizhiReadPath(file.toString()));
    }
}
