package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFileRequestMarkersTest {

    @Test
    fun detectsChineseMarker() {
        val text = "[需要用户选文件: pdf,最多1个]\n请添加合同 PDF。"
        assertTrue(UserFileRequestMarkers.containsRequest(text))
        assertFalse(UserFileRequestMarkers.stripForDisplay(text).contains("[需要用户选文件"))
    }

    @Test
    fun detectsEnglishMarker() {
        assertTrue(UserFileRequestMarkers.containsRequest("[user_pick_files] attach invoice"))
    }

    @Test
    fun noMarker() {
        assertFalse(UserFileRequestMarkers.containsRequest("请把文件放到 imports/"))
    }
}
