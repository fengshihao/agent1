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

    fun resolveFile(workspaceRoot: Path, link: String): File? {
        val raw = link.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("file://")) {
            val abs = File(java.net.URI(raw)).canonicalFile.toPath().normalize()
            val root = workspaceRoot.toAbsolutePath().normalize()
            if (!abs.startsWith(root)) return null
            return abs.toFile().takeIf { it.isFile }
        }
        val relative = raw.removePrefix("./").removePrefix("/")
        val candidate = workspaceRoot.resolve(relative).normalize()
        val root = workspaceRoot.toAbsolutePath().normalize()
        if (!candidate.startsWith(root)) return null
        return candidate.toFile().takeIf { it.isFile }
    }
}
