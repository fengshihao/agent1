package com.dynamicui.demo.productivity.logic.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把已有文件和文本打进诊断 zip。不访问 Android API，便于单测。
 */
class DiagnosticBundleWriter {

    data class NamedText(val entryName: String, val text: String)

    data class NamedDir(val entryPrefix: String, val dir: File)

    data class Request(
        val outputZip: File,
        val trees: List<NamedDir> = emptyList(),
        val texts: List<NamedText> = emptyList(),
        val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    )

    data class Written(
        val zip: File,
        val includedFiles: Int,
        val skipped: List<String>,
    )

    fun write(request: Request): Written {
        request.outputZip.parentFile?.mkdirs()
        val skipped = mutableListOf<String>()
        var included = 0
        ZipOutputStream(BufferedOutputStream(request.outputZip.outputStream())).use { zip ->
            for (text in request.texts) {
                val name = normalizeEntry(text.entryName)
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                included++
            }
            for (tree in request.trees) {
                if (!tree.dir.isDirectory) {
                    skipped += "缺少目录 ${tree.dir.path}"
                    continue
                }
                val prefix = normalizeEntry(tree.entryPrefix).trimEnd('/')
                tree.dir.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    if (file.absolutePath == request.outputZip.absolutePath) return@forEach
                    val relative = file.relativeTo(tree.dir).invariantSeparatorsPath
                    val entryName = if (prefix.isEmpty()) relative else "$prefix/$relative"
                    if (file.length() > request.maxFileBytes) {
                        skipped += "$entryName (${file.length()} bytes)"
                        return@forEach
                    }
                    FileInputStream(file).use { input ->
                        zip.putNextEntry(ZipEntry(normalizeEntry(entryName)))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                    included++
                }
            }
            if (skipped.isNotEmpty()) {
                val note = buildString {
                    appendLine("以下文件未打入包（缺失或超过 ${request.maxFileBytes} 字节）：")
                    skipped.forEach { appendLine("- $it") }
                }
                zip.putNextEntry(ZipEntry("skipped.txt"))
                zip.write(note.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return Written(request.outputZip, included, skipped)
    }

    private fun normalizeEntry(name: String): String {
        return name.replace('\\', '/').trimStart('/')
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES: Long = 8L * 1024L * 1024L
    }
}
