package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibleFileLabelsTest {

    @Test
    fun primaryLabel_prefersDisplayName() {
        assertEquals(
            "合同.pdf",
            AccessibleFileLabels.primaryLabel("imports/合同.pdf", "合同.pdf"),
        )
    }

    @Test
    fun primaryLabel_usesFileNameNotImportsPrefix() {
        assertEquals("chart.png", AccessibleFileLabels.primaryLabel("imports/chart.png", null))
    }

    @Test
    fun parentHint_stripsImportsPrefix() {
        assertEquals("out", AccessibleFileLabels.parentHint("out/report.docx"))
        assertNull(AccessibleFileLabels.parentHint("imports/a.pdf"))
    }
}
