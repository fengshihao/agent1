package com.agent1.android.productivity.logic.business

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * 产物库：汇总所有会话 workspace 内 **AI 生成的文件**，供用户跨会话查找、
 * 删除、分享与插入当前会话。
 *
 * 产物判定：workspace 下除 `imports/`（用户自带附件副本）与 `tmp/`（运行时临时区，
 * 如 webview_exec 中转）以外的常规文件；隐藏文件/目录（`.` 开头）同样不算。
 */
object ArtifactLibraryStore {

    /** 相对 workspace 根的排除前缀（带 `/`，避免误伤 `imports-old.md` 这类文件名）。 */
    private val EXCLUDED_DIR_PREFIXES = listOf("imports/", "tmp/")

    /** 指向某个会话 workspace 内的一个文件；跨层传递的最小标识。 */
    data class ArtifactRef(
        val sessionId: String,
        val workspaceRelativePath: String,
    )

    data class ArtifactEntry(
        val sessionId: String,
        val workspaceRelativePath: String,
        val fileName: String,
        val sizeBytes: Long,
        val lastModifiedMillis: Long,
    ) {
        val stableKey: String
            get() = "$sessionId/$workspaceRelativePath"

        fun toRef(): ArtifactRef = ArtifactRef(sessionId, workspaceRelativePath)
    }

    fun sessionsRoot(context: Context): Path =
        SessionWorkspacePaths.agentRoot(context).resolve("sessions")

    fun listArtifacts(context: Context): List<ArtifactEntry> =
        listArtifacts(sessionsRoot(context))

    /** 纯 Path 版扫描（便于 JVM 单测）：sessions/<id>/workspace 下所有常规文件，按修改时间倒序。 */
    fun listArtifacts(sessionsRoot: Path): List<ArtifactEntry> {
        if (!Files.isDirectory(sessionsRoot)) return emptyList()
        val entries = mutableListOf<ArtifactEntry>()
        // 运行中的会话可能并发增删文件；单个文件读取失败跳过即可，不让整页报错
        runCatching {
            Files.list(sessionsRoot).use { sessions ->
                sessions.filter { Files.isDirectory(it) }.forEach { sessionDir ->
                    val sessionId = sessionDir.fileName?.toString().orEmpty()
                    if (sessionId.isBlank()) return@forEach
                    val workspace = sessionDir.resolve("workspace")
                    if (!Files.isDirectory(workspace)) return@forEach
                    collectWorkspaceArtifacts(workspace, sessionId, entries)
                }
            }
        }
        return entries.sortedWith(
            compareByDescending<ArtifactEntry> { it.lastModifiedMillis }.thenBy { it.stableKey },
        )
    }

    /** relative 是否算 AI 产物（相对 workspace 根，`/` 分隔）。 */
    fun isArtifactRelativePath(relative: String): Boolean {
        val normalized = relative.trim().replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith("/")) return false
        for (prefix in EXCLUDED_DIR_PREFIXES) {
            if (normalized == prefix.trimEnd('/') || normalized.startsWith(prefix)) return false
        }
        return normalized.split('/').none { it.isBlank() || it.startsWith(".") }
    }

    fun resolveArtifactFile(context: Context, ref: ArtifactRef): File? {
        if (ref.sessionId.isBlank() || ref.workspaceRelativePath.isBlank()) return null
        val workspace = SessionWorkspacePaths.workspaceRoot(context, ref.sessionId) ?: return null
        val root = workspace.toAbsolutePath().normalize()
        val candidate = workspace.resolve(ref.workspaceRelativePath).toAbsolutePath().normalize()
        if (!candidate.startsWith(root)) return null
        return candidate.toFile().takeIf { it.isFile }
    }

    fun deleteArtifact(context: Context, ref: ArtifactRef): Boolean {
        val file = resolveArtifactFile(context, ref) ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    private fun collectWorkspaceArtifacts(
        workspace: Path,
        sessionId: String,
        out: MutableList<ArtifactEntry>,
    ) {
        runCatching {
            val root = workspace.toAbsolutePath().normalize()
            Files.walk(workspace).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val abs = file.toAbsolutePath().normalize()
                    if (!abs.startsWith(root)) return@forEach
                    val relative = root.relativize(abs).toString().replace('\\', '/')
                    if (!isArtifactRelativePath(relative)) return@forEach
                    val attrs = runCatching {
                        Files.readAttributes(abs, BasicFileAttributes::class.java)
                    }.getOrNull() ?: return@forEach
                    out.add(
                        ArtifactEntry(
                            sessionId = sessionId,
                            workspaceRelativePath = relative,
                            fileName = relative.substringAfterLast('/'),
                            sizeBytes = attrs.size(),
                            lastModifiedMillis = attrs.lastModifiedTime().toMillis(),
                        ),
                    )
                }
            }
        }
    }
}