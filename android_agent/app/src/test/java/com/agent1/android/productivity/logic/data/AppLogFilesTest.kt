package com.agent1.android.productivity.logic.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogFilesTest {

    @Test
    fun append_writesActiveFile() {
        val dir = tempDir()
        AppLogFiles.append(dir, "hello")
        val active = File(dir, AppLogFiles.ACTIVE_NAME)
        assertEquals("hello\n", active.readText())
    }

    @Test
    fun append_rotatesWhenActiveReachesLimit() {
        val dir = tempDir()
        AppLogFiles.append(dir, "old-line", maxFileBytes = 4)
        AppLogFiles.append(dir, "next", maxFileBytes = 4)
        val active = File(dir, AppLogFiles.ACTIVE_NAME)
        val rotated = dir.listFiles { file -> file.name.startsWith("app-") }.orEmpty()
        assertEquals("next\n", active.readText())
        assertEquals(1, rotated.size)
        assertTrue(rotated[0].readText().contains("old-line"))
    }

    @Test
    fun cleanup_deletesOldRotatedFilesAndKeepsActive() {
        val dir = tempDir()
        AppLogFiles.append(dir, "current")
        val old = File(dir, "app-20000101-000000-000.log").apply {
            writeText("old")
            setLastModified(System.currentTimeMillis() - 10L * 24L * 60L * 60L * 1000L)
        }
        val fresh = File(dir, "app-20990101-000000-000.log").apply {
            writeText("fresh")
            setLastModified(System.currentTimeMillis())
        }

        AppLogFiles.cleanup(dir)

        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(File(dir, AppLogFiles.ACTIVE_NAME).exists())
    }

    @Test
    fun cleanup_dropsOldestRotatedWhenTotalExceedsCap() {
        val dir = tempDir()
        File(dir, AppLogFiles.ACTIVE_NAME).writeText("active")
        val older = File(dir, "app-20200101-000000-000.log").apply {
            writeText("12345")
            setLastModified(1_000L)
        }
        val newer = File(dir, "app-20240101-000000-000.log").apply {
            writeText("abc")
            setLastModified(2_000L)
        }

        AppLogFiles.cleanup(dir, maxAgeMs = Long.MAX_VALUE, maxTotalBytes = 10)

        assertFalse(older.exists())
        assertTrue(newer.exists())
        assertTrue(File(dir, AppLogFiles.ACTIVE_NAME).exists())
    }

    private fun tempDir(): File {
        return File.createTempFile("app-logs", "").apply {
            delete()
            mkdirs()
        }
    }
}
