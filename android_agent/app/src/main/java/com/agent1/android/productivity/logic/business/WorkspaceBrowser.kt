package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * 会话 workspace 内文件树（供 ask_user `workspace_files` 与 UI 浏览）。
 * 隐藏 `tmp/` 与点文件；`imports/` 仍可选，但展示名由 [AccessibleFileLabels] 处理。
 */
object WorkspaceBrowser {

    data class Entry(
        val relativePath: String,
        val name: String,
        val isDirectory: Boolean,
    )

    fun isHiddenInTree(relativePath: String): Boolean {
        val normalized = relativePath.trim().replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return false
        if (normalized == "tmp" || normalized.startsWith("tmp/")) return true
        return normalized.split('/').any { it.isBlank() || it.startsWith(".") }
    }

    fun listChildren(workspaceRoot: Path, relativeDir: String): List<Entry> {
        if (!Files.isDirectory(workspaceRoot)) return emptyList()
        val dir = resolveDir(workspaceRoot, relativeDir) ?: return emptyList()
        if (!dir.isDirectory()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.map { path ->
                val name = path.fileName?.toString().orEmpty()
                if (name.isEmpty() || name.startsWith(".")) return@map null
                val relative = joinRelative(relativeDir, name)
                if (isHiddenInTree(relative)) return@map null
                Entry(
                    relativePath = relative,
                    name = name,
                    isDirectory = path.isDirectory(),
                )
            }
                .filterNotNull()
                .sortedWith(
                    compareBy<Entry> { !it.isDirectory }.thenBy { it.name.lowercase() },
                )
                .toList()
        }
    }

    fun isSelectableFile(workspaceRoot: Path, relativePath: String): Boolean {
        if (isHiddenInTree(relativePath)) return false
        val file = resolveFile(workspaceRoot, relativePath) ?: return false
        return file.isRegularFile()
    }

    private fun resolveDir(workspaceRoot: Path, relativeDir: String): Path? {
        val normalized = relativeDir.trim().replace('\\', '/').trim('/')
        val target = if (normalized.isEmpty()) {
            workspaceRoot
        } else {
            workspaceRoot.resolve(normalized).normalize()
        }
        if (!target.startsWith(workspaceRoot.normalize())) return null
        return target
    }

    private fun resolveFile(workspaceRoot: Path, relativePath: String): Path? {
        val normalized = relativePath.trim().replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        val target = workspaceRoot.resolve(normalized).normalize()
        if (!target.startsWith(workspaceRoot.normalize())) return null
        return target
    }

    private fun joinRelative(parent: String, name: String): String {
        val base = parent.trim().replace('\\', '/').trim('/')
        return if (base.isEmpty()) name else "$base/$name"
    }
}
