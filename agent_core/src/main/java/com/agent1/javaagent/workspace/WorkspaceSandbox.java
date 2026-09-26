package com.agent1.javaagent.workspace;

import java.nio.file.Path;

/**
 * 将会话工作区内的相对路径解析为绝对路径，并用路径段前缀校验防止逃逸（例如 {@code workspace} 不能匹配 {@code workspace-other}）。
 */
public final class WorkspaceSandbox {

    private final Path root;

    public WorkspaceSandbox(Path workspaceRoot) {
        this.root = workspaceRoot.toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 将相对路径锚定在工作区根下，normalize 后做段前缀 containment 校验。
     */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("path is empty");
        }
        Path input = Path.of(relativePath.trim());
        if (input.isAbsolute()) {
            throw new SecurityException("absolute path not allowed: " + relativePath.trim());
        }
        Path resolved = root.resolve(input).normalize();
        ensureContained(resolved);
        return resolved;
    }

    /**
     * 将工作区内的绝对路径转为相对路径（正斜杠），供模型与界面展示。
     */
    public String relativize(Path absolute) {
        if (absolute == null) {
            throw new SecurityException("path is null");
        }
        Path normalized = absolute.toAbsolutePath().normalize();
        ensureContained(normalized);
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private void ensureContained(Path candidate) {
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
