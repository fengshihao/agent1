package com.dynamicui.demo.productivity.logic.data

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBundleWriterTest {

    @Test
    fun writesTranscriptsAndSkipsOversizedFiles() {
        val parent = File.createTempFile("agent1-diag", "").apply {
            delete()
            mkdirs()
        }
        val root = File(parent, "data").apply { mkdirs() }
        val session = File(root, "sessions/s1")
        session.mkdirs()
        File(session, "transcript.jsonl").writeText("hi\n")
        File(session, "big.bin").writeBytes(ByteArray(32) { 1 })

        val zip = File(parent, "out.zip")
        val written = DiagnosticBundleWriter().write(
            DiagnosticBundleWriter.Request(
                outputZip = zip,
                trees = listOf(DiagnosticBundleWriter.NamedDir("agent1", root)),
                texts = listOf(DiagnosticBundleWriter.NamedText("README.txt", "hello")),
                maxFileBytes = 8,
            ),
        )

        ZipFile(written.zip).use { archive ->
            val names = archive.entries().toList().map { it.name }.toSet()
            assertTrue(names.contains("README.txt"))
            assertTrue(names.contains("agent1/sessions/s1/transcript.jsonl"))
            assertTrue(names.contains("skipped.txt"))
            assertTrue(names.none { it.endsWith("big.bin") })
            assertTrue(names.none { it == "agent1/out.zip" || it.endsWith("/out.zip") })
        }
        assertEquals(1, written.skipped.size)
        parent.deleteRecursively()
    }
}
