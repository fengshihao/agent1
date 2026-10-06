package com.agent1.android.productivity.logic.business

import android.content.Context
import java.io.File
import java.nio.file.Path

/** 会话工作区路径（与 {@code FileSessionStore} 布局一致）。 */
object SessionWorkspacePaths {

    fun agentRoot(context: Context): Path =
        context.filesDir.toPath().resolve("agent1")

    fun workspaceRoot(context: Context, sessionId: String): Path? {
        if (sessionId.isBlank()) return null
        return agentRoot(context)
            .resolve("sessions")
            .resolve(sessionId)
            .resolve("workspace")
    }

    /** 模型/工具常带 `workspace/` 或 `./` 前缀；解析与 UI 链接统一去掉。 */
    fun normalizeWorkspaceRelativePath(raw: String): String =
        canonicalWorkspaceRelative(raw, null)

    /**
     * 转为会话 workspace 内相对路径。模型常输出绝对路径（含 `.../sessions/.../workspace/...`）。
     */
    fun canonicalWorkspaceRelative(raw: String, workspaceRoot: Path?): String {
        var path = raw.trim().replace('\\', '/')
        if (path.startsWith("file://")) return path
        val input = runCatching { java.nio.file.Paths.get(path) }.getOrNull()
        if (input != null && input.isAbsolute) {
            val abs = input.normalize()
            if (workspaceRoot != null) {
                val root = workspaceRoot.toAbsolutePath().normalize()
                if (abs.startsWith(root)) {
                    return root.relativize(abs).toString().replace('\\', '/')
                }
            }
            val unix = abs.toString().replace('\\', '/')
            val marker = "/workspace/"
            val idx = unix.lastIndexOf(marker)
            if (idx >= 0) {
                val tail = unix.substring(idx + marker.length)
                if (tail.isNotBlank()) {
                    return stripWorkspaceRelativePrefixes(tail)
                }
            }
        }
        return stripWorkspaceRelativePrefixes(path)
    }

    private fun stripWorkspaceRelativePrefixes(path: String): String {
        var p = path.trim().replace('\\', '/')
        p = p.removePrefix("./")
        while (p.startsWith("/")) {
            p = p.removePrefix("/")
        }
        p = p.removePrefix("workspace/")
        return p
    }

    /** 展示文本里的 workspace 绝对路径改成相对路径。 */
    fun scrubWorkspaceAbsolute(text: String, workspaceRoot: Path?): String {
        if (workspaceRoot == null || text.isEmpty()) return text
        val prefix = workspaceRoot.toAbsolutePath().normalize().toString()
        if (!text.contains(prefix)) return text
        return text.replace("$prefix/", "").replace(prefix, ".")
    }

    fun resolveFile(workspaceRoot: Path, link: String): File? {
        val raw = link.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("file://")) {
            val abs = File(java.net.URI(raw)).canonicalFile.toPath().normalize()
            val root = workspaceRoot.toAbsolutePath().normalize()
            if (!abs.startsWith(root)) return null
            return abs.toFile().takeIf { it.isFile }
        }
        val relative = canonicalWorkspaceRelative(raw, workspaceRoot)
        if (relative.isBlank()) return null
        val candidate = workspaceRoot.resolve(relative).normalize()
        val root = workspaceRoot.toAbsolutePath().normalize()
        if (!candidate.startsWith(root)) return null
        return candidate.toFile().takeIf { it.isFile }
    }
}
