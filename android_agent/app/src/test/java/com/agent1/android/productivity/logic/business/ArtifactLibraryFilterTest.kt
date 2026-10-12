package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactLibraryFilterTest {

    @Test
    fun shelfFor_primaryDeliverables() {
        assertEquals(ArtifactLibraryFilter.Shelf.PRIMARY, ArtifactLibraryFilter.shelfFor("out/report.docx"))
        assertEquals(ArtifactLibraryFilter.Shelf.PRIMARY, ArtifactLibraryFilter.shelfFor("deck.pptx"))
        assertEquals(ArtifactLibraryFilter.Shelf.PRIMARY, ArtifactLibraryFilter.shelfFor("index.html"))
        assertEquals(ArtifactLibraryFilter.Shelf.PRIMARY, ArtifactLibraryFilter.shelfFor("notes.md"))
    }

    @Test
    fun shelfFor_auxiliaryScripts() {
        assertEquals(ArtifactLibraryFilter.Shelf.AUXILIARY, ArtifactLibraryFilter.shelfFor("a8f3c2.js"))
        assertEquals(ArtifactLibraryFilter.Shelf.AUXILIARY, ArtifactLibraryFilter.shelfFor("run_js/helper.mjs"))
        assertEquals(ArtifactLibraryFilter.Shelf.AUXILIARY, ArtifactLibraryFilter.shelfFor("manifest.json"))
    }

    @Test
    fun matches_respectsShelfTypeAndQuery() {
        assertTrue(
            ArtifactLibraryFilter.matches(
                workspaceRelativePath = "out/index.html",
                shelf = ArtifactLibraryFilter.Shelf.PRIMARY,
                typeFilter = ArtifactLibraryFilter.TypeFilter.HTML,
                query = "index",
                sessionTitle = "周报",
                fileName = "index.html",
            ),
        )
        assertFalse(
            ArtifactLibraryFilter.matches(
                workspaceRelativePath = "out/index.html",
                shelf = ArtifactLibraryFilter.Shelf.AUXILIARY,
                typeFilter = ArtifactLibraryFilter.TypeFilter.ALL,
                query = "",
                sessionTitle = "",
                fileName = "index.html",
            ),
        )
        assertFalse(
            ArtifactLibraryFilter.matches(
                workspaceRelativePath = "chunk.js",
                shelf = ArtifactLibraryFilter.Shelf.AUXILIARY,
                typeFilter = ArtifactLibraryFilter.TypeFilter.HTML,
                query = "",
                sessionTitle = "",
                fileName = "chunk.js",
            ),
        )
        assertTrue(
            ArtifactLibraryFilter.matches(
                workspaceRelativePath = "chunk.js",
                shelf = ArtifactLibraryFilter.Shelf.AUXILIARY,
                typeFilter = ArtifactLibraryFilter.TypeFilter.CODE,
                query = "chunk",
                sessionTitle = "x",
                fileName = "chunk.js",
            ),
        )
    }
}
