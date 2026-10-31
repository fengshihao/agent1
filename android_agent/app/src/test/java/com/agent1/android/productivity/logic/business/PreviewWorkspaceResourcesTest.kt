package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewWorkspaceResourcesTest {

    @Test
    fun documentUrlEncodesSegments() {
        assertEquals(
            "https://appassets.androidplatform.net/out/%E8%B7%AF%E4%B9%A6.html",
            PreviewWorkspaceResources.documentUrl("out/路书.html"),
        )
        assertEquals(
            "https://appassets.androidplatform.net/notes/",
            PreviewWorkspaceResources.directoryUrl("notes/a.md"),
        )
        assertEquals(
            "https://appassets.androidplatform.net/",
            PreviewWorkspaceResources.directoryUrl("a.md"),
        )
    }

    @Test
    fun lookupServesWorkspaceImageAndRejectsEscape() {
        val root = Files.createTempDirectory("preview-res")
        val image = root.resolve("out/assets/a.png")
        Files.createDirectories(image.parent)
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        Files.write(image, bytes)

        val hit = PreviewWorkspaceResources.lookup(
            root,
            "https://appassets.androidplatform.net/out/assets/a.png",
        )
        assertTrue(hit is PreviewWorkspaceResources.Lookup.Found)
        val found = hit as PreviewWorkspaceResources.Lookup.Found
        assertEquals("image/png", found.mimeType)
        assertArrayEquals(bytes, found.bytes)

        assertTrue(
            PreviewWorkspaceResources.lookup(root, "https://cdn.example/a.png")
                is PreviewWorkspaceResources.Lookup.NotOurs,
        )
        assertTrue(
            PreviewWorkspaceResources.lookup(root, "https://appassets.androidplatform.net/../secret.png")
                is PreviewWorkspaceResources.Lookup.Missing,
        )
        assertTrue(
            PreviewWorkspaceResources.lookup(root, "https://appassets.androidplatform.net/out/missing.png")
                is PreviewWorkspaceResources.Lookup.Missing,
        )
    }

    @Test
    fun lookupDecodesUnicodePath() {
        val root = Files.createTempDirectory("preview-res-utf")
        val image = root.resolve("out/路书.png")
        Files.createDirectories(image.parent)
        Files.write(image, byteArrayOf(1, 2, 3))
        val hit = PreviewWorkspaceResources.lookup(
            root,
            PreviewWorkspaceResources.documentUrl("out/路书.png"),
        )
        assertTrue(hit is PreviewWorkspaceResources.Lookup.Found)
    }
}
