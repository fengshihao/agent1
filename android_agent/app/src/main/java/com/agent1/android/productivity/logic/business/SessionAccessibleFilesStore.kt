package com.agent1.android.productivity.logic.business

import android.content.Context
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** 会话级「用户提供的可访问文件」清单（`sessions/<id>/accessible-files.json`）。 */
object SessionAccessibleFilesStore {

    private const val FILE_NAME = "accessible-files.json"

    data class Entry(
        val workspaceRelativePath: String,
        val displayName: String,
        val addedAt: String,
    )

    fun sessionDir(context: Context, sessionId: String): Path? {
        if (sessionId.isBlank()) return null
        return SessionWorkspacePaths.agentRoot(context)
            .resolve("sessions")
            .resolve(sessionId)
    }

    fun listEntries(context: Context, sessionId: String): List<Entry> {
        val file = metaFile(context, sessionId) ?: return emptyList()
        if (!Files.isRegularFile(file)) return emptyList()
        return try {
            val root = JSONObject(readUtf8(file))
            val arr = root.optJSONArray("files") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val path = o.optString("path", "").trim()
                    if (path.isEmpty()) continue
                    add(
                        Entry(
                            workspaceRelativePath = path,
                            displayName = o.optString("displayName", path.substringAfterLast('/')),
                            addedAt = o.optString("addedAt", ""),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun listRelativePaths(context: Context, sessionId: String): List<String> =
        listEntries(context, sessionId).map { it.workspaceRelativePath }

    fun addEntries(context: Context, sessionId: String, newPaths: List<String>, displayNames: List<String>) {
        if (newPaths.isEmpty()) return
        val dir = sessionDir(context, sessionId) ?: return
        Files.createDirectories(dir)
        val existing = listEntries(context, sessionId).associateBy { it.workspaceRelativePath }.toMutableMap()
        val now = Instant.now().toString()
        newPaths.forEachIndexed { index, path ->
            val name = displayNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/')
            existing[path] = Entry(path, name, now)
        }
        write(dir, existing.values.toList())
    }

    fun formatForSystemPrompt(context: Context, sessionId: String): String {
        val entries = listEntries(context, sessionId)
        if (entries.isEmpty()) return ""
        val lines = entries.joinToString("\n") { "  - ${it.workspaceRelativePath}" }
        return """
            - 本会话用户提供的可访问文件（read_file 等工作区相对路径）：
            $lines
            """.trimIndent()
    }

    private fun metaFile(context: Context, sessionId: String): Path? =
        sessionDir(context, sessionId)?.resolve(FILE_NAME)

    private fun write(sessionDir: Path, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("path", e.workspaceRelativePath)
                    .put("displayName", e.displayName)
                    .put("addedAt", e.addedAt),
            )
        }
        writeUtf8(sessionDir.resolve(FILE_NAME), JSONObject().put("files", arr).toString(2))
    }

    private fun readUtf8(path: Path): String =
        Files.newInputStream(path).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    private fun writeUtf8(path: Path, content: String) {
        Files.newOutputStream(path).bufferedWriter(StandardCharsets.UTF_8).use { it.write(content) }
    }
}
