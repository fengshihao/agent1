package com.agent1.android.productivity.logic.business

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportTest {

    @Test
    fun deleteAllDiagnosticZips_removesOnlyZipInDiagnosticsDir() {
        val root = File.createTempFile("agent1-diag-test", "").apply {
            delete()
            mkdirs()
        }
        val diagnostics = File(root, "diagnostics").apply { mkdirs() }
        val zip = File(diagnostics, "agent1-diag-20260101.zip").apply { writeText("x") }
        val keep = File(diagnostics, "note.txt").apply { writeText("y") }

        DiagnosticExport.deleteAllDiagnosticZips(diagnostics)

        assertFalse(zip.exists())
        assertTrue(keep.exists())
    }

    @Test
    fun cleanupStaleDiagnosticExports_deletesOldZipsOnly() {
        val diagnostics = File.createTempFile("agent1-diag-stale", "").apply {
            delete()
            mkdirs()
        }
        val old = File(diagnostics, "old.zip").apply {
            writeText("a")
            setLastModified(System.currentTimeMillis() - 48L * 60L * 60L * 1000L)
        }
        val fresh = File(diagnostics, "fresh.zip").apply { writeText("b") }

        DiagnosticExport.cleanupStaleDiagnosticExports(diagnostics, 24L * 60L * 60L * 1000L)

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun deleteExportZip_rejectsPathsOutsideDiagnostics() {
        val outside = File.createTempFile("agent1-out", ".zip")
        outside.writeText("z")
        DiagnosticExport.deleteExportZip(outside)
        assertTrue(outside.exists())
        outside.delete()
    }
}
