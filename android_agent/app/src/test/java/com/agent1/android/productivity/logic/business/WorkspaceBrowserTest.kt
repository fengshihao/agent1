package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBrowserTest {

    @Test
    fun isHiddenInTree_skipsTmpAndDotFiles() {
        assertTrue(WorkspaceBrowser.isHiddenInTree("tmp/cache.bin"))
        assertTrue(WorkspaceBrowser.isHiddenInTree(".hidden"))
        assertFalse(WorkspaceBrowser.isHiddenInTree("imports/a.pdf"))
    }

    @Test
    fun listChildren_listsWorkspaceRoot() {
        val root = Files.createTempDirectory("ws-browser")
        Files.writeString(root.resolve("hello.txt"), "hi")
        Files.createDirectories(root.resolve("out"))
        val children = WorkspaceBrowser.listChildren(root, "")
        assertTrue(children.any { it.name == "hello.txt" && !it.isDirectory })
        assertTrue(children.any { it.name == "out" && it.isDirectory })
    }
}
