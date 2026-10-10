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
        Path written = agentRoot.resolve("docs/system/directories.md");
        Files.createDirectories(written.getParent());
        Files.writeString(written, "# fixture\n");
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
    void stripsEchoedSessionsWorkspacePrefix() throws Exception {
        // 工具回显 agentRoot 相对路径（sessions/<id>/workspace/...），模型原样回传时也应解析成功
        Path file = sandbox.getRoot().resolve("out/deck.pptx");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "pptx");
        assertEquals(
            file.toAbsolutePath().normalize(),
            sandbox.resolveRead("sessions/some-session/workspace/out/deck.pptx")
        );
        assertEquals(
            file.toAbsolutePath().normalize(),
            sandbox.resolveWrite("sessions/some-session/workspace/out/deck.pptx")
        );
    }

    @Test
    void displayPathPrefersWorkspaceRelativeUnderAgentRoot() throws Exception {
        // 手机真机回归：workspace 物理在 agentRoot/sessions/<id>/workspace 下时，
        // 回显必须是 workspace 相对路径（与模型输入一致），不再带 sessions/<id>/workspace/ 前缀
        Path agentRoot = temp.resolve("agentRoot5");
        Files.createDirectories(agentRoot);
        Path workspace = agentRoot.resolve("sessions/session-9/workspace");
        WorkspaceSandbox withAgent = new WorkspaceSandbox(workspace, agentRoot);
        Path file = workspace.resolve("out/deck.pptx");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "pptx");
        assertEquals("out/deck.pptx", withAgent.displayPath(file));
        assertEquals("out/deck.pptx", withAgent.relativize(file));
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

    @Test
    void resolveReadAcceptsWorkspaceAbsolutePath() throws Exception {
        Path file = sandbox.getRoot().resolve("notes/a.txt");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "hi");
        assertEquals(file.toAbsolutePath().normalize(), sandbox.resolveRead(file.toString()));
        assertEquals(file.toAbsolutePath().normalize(), sandbox.resolveWrite(file.toString()));
    }

    @Test
    void resolveReadAcceptsAgentDocAbsolutePath() throws Exception {
        Path agentRoot = temp.resolve("agentRoot4");
        AgentHomeBootstrap.ensure(agentRoot);
        WorkspaceSandbox withAgent = new WorkspaceSandbox(temp.resolve("ws4"), agentRoot);
        Path doc = agentRoot.resolve("docs/system/directories.md");
        assertEquals(doc.toAbsolutePath().normalize(), withAgent.resolveRead(doc.toString()));
    }

    @Test
    void symlinkInsideWorkspaceRejectedForReadAndWrite() throws Exception {
        Path outside = temp.resolve("outside.txt");
        Files.writeString(outside, "secret");
        Files.createDirectories(sandbox.getRoot());
        Path link = sandbox.getRoot().resolve("leak.txt");
        Files.createSymbolicLink(link, outside);

        assertThrows(SecurityException.class, () -> sandbox.resolveRead("leak.txt"));
        assertThrows(SecurityException.class, () -> sandbox.resolveWrite("leak.txt"));
    }

    @Test
    void symlinkDirectoryInsideWorkspaceRejected() throws Exception {
        Path outsideDir = temp.resolve("outsideDir");
        Files.createDirectories(outsideDir);
        Files.createDirectories(sandbox.getRoot());
        Path linkDir = sandbox.getRoot().resolve("leakdir");
        Files.createSymbolicLink(linkDir, outsideDir);

        assertThrows(SecurityException.class, () -> sandbox.resolveRead("leakdir/inner.txt"));
        assertThrows(SecurityException.class, () -> sandbox.resolveWrite("leakdir/inner.txt"));
        assertThrows(SecurityException.class, () -> sandbox.resolveRead("leakdir"));
    }

    @Test
    void danglingSymlinkInsideWorkspaceRejected() throws Exception {
        Files.createDirectories(sandbox.getRoot());
        Path link = sandbox.getRoot().resolve("dangling");
        Files.createSymbolicLink(link, temp.resolve("missing-target"));

        assertThrows(SecurityException.class, () -> sandbox.resolveRead("dangling"));
    }

    @Test
    void regularFilesInsideWorkspaceStillPass() throws Exception {
        Path file = sandbox.getRoot().resolve("plain/a.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hi");
        assertEquals(file.toAbsolutePath().normalize(), sandbox.resolveRead("plain/a.txt"));
        assertEquals(file.toAbsolutePath().normalize(), sandbox.resolveWrite("plain/a.txt"));
    }
}
