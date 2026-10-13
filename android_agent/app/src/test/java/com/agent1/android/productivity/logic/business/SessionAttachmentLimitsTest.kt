package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionAttachmentLimitsTest {

    @Test
    fun maxFilesPerSession_isTen() {
        assertEquals(10, SessionAttachmentLimits.MAX_FILES_PER_SESSION)
    }

    @Test
    fun maxFileSize_isFiftyMegabytes() {
        assertEquals(50, SessionAttachmentLimits.MAX_FILE_SIZE_MB)
        assertEquals(50L * 1024L * 1024L, SessionAttachmentLimits.MAX_FILE_BYTES)
    }
}
