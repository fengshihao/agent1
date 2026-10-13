package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import java.nio.file.Path

/** 从会话 workspace 删除用户已取消的 imports 副本。 */
object WorkspaceAccessibleFileDelete {

    fun deleteRelativeFiles(workspaceRoot: Path, relativePaths: List<String>) {
        if (relativePaths.isEmpty()) return
        val root = workspaceRoot.toAbsolutePath().normalize()
        relativePaths.forEach { rel ->
            val normalized = rel.trim().replace('\\', '/')
            if (normalized.isEmpty() || normalized.contains("..")) return@forEach
            val target = root.resolve(normalized).normalize()
            if (!target.startsWith(root)) return@forEach
            Files.deleteIfExists(target)
        }
    }
}
