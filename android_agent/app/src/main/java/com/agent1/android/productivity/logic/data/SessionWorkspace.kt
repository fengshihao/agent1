package com.agent1.android.productivity.logic.data

import android.content.Context
import com.agent1.android.productivity.logic.business.ProductivityGatewayProvider
import java.io.File
import java.nio.file.Path

/** 当前会话工作区路径解析（与 {@code FileSessionStore} 布局一致）。 */
object SessionWorkspace {

    fun workspaceRoot(context: Context, sessionId: String): Path? {
        if (sessionId.isBlank()) return null
        return ProductivityGatewayProvider.agentRoot(context)
            .resolve("sessions")
            .resolve(sessionId)
            .resolve("workspace")
    }

    fun resolveFile(context: Context, sessionId: String, link: String): File? {
        val root = workspaceRoot(context, sessionId)?.toAbsolutePath()?.normalize() ?: return null
        val raw = link.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("file://")) {
            val abs = java.io.File(java.net.URI(raw)).canonicalFile.toPath().normalize()
            if (!abs.startsWith(root)) return null
            return abs.toFile().takeIf { it.isFile }
        }
        val relative = raw.removePrefix("./").removePrefix("/")
        val candidate = root.resolve(relative).normalize()
        if (!candidate.startsWith(root)) return null
        val file = candidate.toFile()
        return file.takeIf { it.isFile }
    }
}
