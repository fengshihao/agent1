package com.agent1.javaagent.workspace;

import com.agent1.javaagent.agent.AgentReadScope;
import java.nio.file.Path;

/**
 * 会话工作区路径解析；可选 {@link #agentRoot()} 时 {@link #resolveRead(String)} 还允许
 * agentRoot 下 {@code docs/system}、{@code docs/capabilities} 只读（与 write 工具隔离）。
 */
public final class WorkspaceSandbox {

    private final Path root;
    private final Path agentRoot;

    public WorkspaceSandbox(Path workspaceRoot) {
        this(workspaceRoot, null);
    }

    public WorkspaceSandbox(Path workspaceRoot, Path agentRoot) {
        this.root = workspaceRoot.toAbsolutePath().normalize();
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    public Path agentRoot() {
        return agentRoot;
    }

    public boolean isAgentDocPath(String relativePath) {
        String p = normalizeRelative(relativePath);
        return p.startsWith("docs/system/") || p.equals("docs/system")
            || p.startsWith("docs/capabilities/") || p.equals("docs/capabilities");
    }

    /**
     * 读/list/grep/glob：workspace 相对路径，或 {@code docs/system|capabilities/...}（需配置 agentRoot）。
     */
    public Path resolveRead(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("path is empty");
        }
        String trimmed = relativePath.trim();
        if (isAgentDocPath(trimmed)) {
            if (agentRoot == null) {
                throw new SecurityException("agent docs not available (no agentRoot): " + trimmed);
            }
            return AgentReadScope.resolveDocPath(agentRoot, trimmed);
        }
        return resolveWorkspace(trimmed);
    }

    /** 写/edit：仅 workspace；禁止 docs/ 与越界。 */
    public Path resolveWrite(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("path is empty");
        }
        String trimmed = relativePath.trim();
        if (isAgentDocPath(trimmed) || trimmed.startsWith("docs/")) {
            throw new SecurityException("path is read-only agent docs: " + trimmed);
        }
        return resolveWorkspace(trimmed);
    }

    /** @deprecated 读路径请用 {@link #resolveRead(String)}；写路径用 {@link #resolveWrite(String)}。 */
    @Deprecated
    public Path resolve(String relativePath) {
        return resolveWrite(relativePath);
    }

    /**
     * 展示用相对路径：agent 文档保留 {@code docs/...}，工作区文件相对 workspace。
     */
    public String displayPath(Path absolute) {
        if (absolute == null) {
            throw new SecurityException("path is null");
        }
        Path normalized = absolute.toAbsolutePath().normalize();
        if (agentRoot != null) {
            Path agent = agentRoot.toAbsolutePath().normalize();
            if (normalized.startsWith(agent)) {
                return agent.relativize(normalized).toString().replace('\\', '/');
            }
        }
        ensureContainedInWorkspace(normalized);
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    /**
     * 将工作区内的绝对路径转为相对路径（正斜杠），供模型与界面展示。
     */
    public String relativize(Path absolute) {
        return displayPath(absolute);
    }

    /**
     * 微智 {@code grep}/{@code glob} 的路径参数：仅 workspace 相对路径，或 {@code docs/system|capabilities/...}。
     * 模型常会传 agentRoot / workspace 绝对路径（{@link #resolveRead} 不接受绝对路径，但展示里会出现绝对 agentRoot）。
     */
    public String toWeizhiReadPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String trimmed = normalizeRelative(rawPath);
        if (isAgentDocPath(trimmed)) {
            return trimmed;
        }
        if ("docs".equals(trimmed)) {
            return agentRoot == null ? trimmed : "docs/system";
        }

        Path input = Path.of(trimmed);
        if (!input.isAbsolute()) {
            return normalizeWorkspaceRelative(trimmed);
        }

        Path abs = input.normalize();
        if (abs.startsWith(root)) {
            return root.relativize(abs).toString().replace('\\', '/');
        }
        if (agentRoot != null) {
            Path agent = agentRoot;
            if (abs.equals(agent)) {
                return "docs/system";
            }
            if (abs.startsWith(agent)) {
                String rel = agent.relativize(abs).toString().replace('\\', '/');
                if (isAgentDocPath(rel)) {
                    return rel;
                }
                if ("docs".equals(rel)) {
                    return "docs/system";
                }
            }
        }
        return trimmed;
    }

    private Path resolveWorkspace(String relativePath) {
        String normalized = normalizeWorkspaceRelative(relativePath);
        Path input = Path.of(normalized);
        if (input.isAbsolute()) {
            throw new SecurityException("absolute path not allowed: " + relativePath);
        }
        Path resolved = root.resolve(input).normalize();
        ensureContainedInWorkspace(resolved);
        return resolved;
    }

    /**
     * 模型常把「工作区根」误写成路径前缀 {@code workspace/}（环境摘要里目录名也是 workspace），
     * 统一剥掉冗余前缀，避免 workspace/workspace/... 嵌套。
     */
    static String normalizeWorkspaceRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("path is empty");
        }
        String p = relativePath.trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        while (p.startsWith("workspace/")) {
            p = p.substring("workspace/".length());
        }
        if (p.isEmpty()) {
            throw new SecurityException("path is empty");
        }
        return p;
    }

    private static String normalizeRelative(String relativePath) {
        return relativePath.trim().replace('\\', '/');
    }

    private void ensureContainedInWorkspace(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (normalized.getNameCount() < root.getNameCount()) {
            throw new SecurityException("path escapes workspace: " + normalized);
        }
        for (int i = 0; i < root.getNameCount(); i++) {
            if (!root.getName(i).equals(normalized.getName(i))) {
                throw new SecurityException("path escapes workspace: " + normalized);
            }
        }
    }
}
