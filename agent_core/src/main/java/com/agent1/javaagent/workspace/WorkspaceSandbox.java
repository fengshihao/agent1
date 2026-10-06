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
     * 读/list/grep/glob：先收成逻辑路径，再解析。
     * 逻辑路径是 workspace 相对路径，或 {@code docs/system|capabilities/...}。
     */
    public Path resolveRead(String relativePath) {
        String logical = logicalPath(relativePath);
        if (isAgentDocPath(logical)) {
            if (agentRoot == null) {
                throw new SecurityException("agent docs not available (no agentRoot): " + logical);
            }
            return AgentReadScope.resolveDocPath(agentRoot, logical);
        }
        return resolveWorkspace(logical);
    }

    /** 写/edit：仅 workspace；禁止 docs/ 与越界。绝对路径会先收成逻辑路径。 */
    public Path resolveWrite(String relativePath) {
        String logical = logicalPath(relativePath);
        if (isAgentDocPath(logical) || logical.startsWith("docs/") || "docs".equals(logical)) {
            throw new SecurityException("path is read-only agent docs: " + logical);
        }
        return resolveWorkspace(logical);
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
     * 模型路径的唯一入口：收成逻辑路径。
     * workspace 内文件为相对路径；只读文档为 {@code docs/system/...} 或 {@code docs/capabilities/...}。
     * 工作区或 agentRoot 下的绝对路径会改写；区外绝对路径拒绝。
     */
    public String logicalPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new SecurityException("path is empty");
        }
        String trimmed = normalizeRelative(rawPath);
        if (isAgentDocPath(trimmed)) {
            return trimmed;
        }
        if ("docs".equals(trimmed)) {
            return agentRoot == null ? trimmed : "docs/system";
        }
        if (".".equals(trimmed)) {
            return trimmed;
        }

        Path input = Path.of(trimmed);
        if (!input.isAbsolute()) {
            return normalizeWorkspaceRelative(trimmed);
        }

        Path abs = input.normalize();
        if (abs.startsWith(root)) {
            String rel = root.relativize(abs).toString().replace('\\', '/');
            return rel.isEmpty() ? "." : rel;
        }
        if (agentRoot != null && abs.startsWith(agentRoot)) {
            if (abs.equals(agentRoot)) {
                return "docs/system";
            }
            String rel = agentRoot.relativize(abs).toString().replace('\\', '/');
            if (isAgentDocPath(rel) || "docs".equals(rel)) {
                return "docs".equals(rel) ? "docs/system" : rel;
            }
            throw new SecurityException("absolute path is outside readable docs: " + rawPath);
        }
        throw new SecurityException("absolute path not allowed: " + rawPath);
    }

    /** @deprecated 使用 {@link #logicalPath(String)}。 */
    @Deprecated
    public String toWeizhiReadPath(String rawPath) {
        return logicalPath(rawPath);
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
