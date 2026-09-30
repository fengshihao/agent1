package com.agent1.javaagent.agent;

import java.nio.file.Path;

/** agentRoot 下只读文档路径解析（docs/system、docs/capabilities）。 */
public final class AgentReadScope {

    private AgentReadScope() {
    }

    public static Path resolveDocPath(Path agentRoot, String relativePath) {
        if (agentRoot == null) {
            throw new SecurityException("agentRoot is not configured");
        }
        String trimmed = relativePath == null ? "" : relativePath.trim();
        if (trimmed.isEmpty()) {
            throw new SecurityException("path is empty");
        }
        if (trimmed.startsWith("/") || trimmed.contains("\\")) {
            throw new SecurityException("absolute path not allowed: " + trimmed);
        }
        Path root = agentRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(trimmed.replace('\\', '/')).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("path escapes agentRoot: " + trimmed);
        }
        Path rel = root.relativize(resolved);
        String relUnix = rel.toString().replace('\\', '/');
        if (!relUnix.startsWith("docs/system/") && !relUnix.equals("docs/system")
            && !relUnix.startsWith("docs/capabilities/") && !relUnix.equals("docs/capabilities")) {
            throw new SecurityException("agent docs read-only scope is docs/system and docs/capabilities: " + trimmed);
        }
        return resolved;
    }

    public static Path resolveCatalogRoot(Path agentRoot) {
        Path root = agentRoot.toAbsolutePath().normalize();
        return root.resolve("shared/catalog").normalize();
    }
}
