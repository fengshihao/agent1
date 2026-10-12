package com.agent1.android.productivity.logic.business

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionAccessibleFilesStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun removeEntries_dropsPaths() {
        val sessionId = "test-remove-files-${System.nanoTime()}"
        SessionAccessibleFilesStore.addEntries(
            context,
            sessionId,
            listOf("imports/a.pdf", "out/b.txt"),
            listOf("a.pdf", "b.txt"),
        )
        SessionAccessibleFilesStore.removeEntries(context, sessionId, listOf("imports/a.pdf"))
        val paths = SessionAccessibleFilesStore.listRelativePaths(context, sessionId)
        assertEquals(listOf("out/b.txt"), paths)
        assertTrue(paths.none { it.contains("imports/a.pdf") })
    }
}
