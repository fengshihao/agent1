package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlPreviewNavigationTest {

    @Test
    fun mapSchemesLeaveThePreviewShell() {
        assertEquals(
            HtmlPreviewNavigation.Action.OpenOutside,
            HtmlPreviewNavigation.classify("amapuri://route/plan/?t=0"),
        )
        assertEquals(
            HtmlPreviewNavigation.Action.OpenOutside,
            HtmlPreviewNavigation.classify("androidamap://route?t=0"),
        )
        assertEquals(
            HtmlPreviewNavigation.Action.OpenOutside,
            HtmlPreviewNavigation.classify("baidumap://map/direction?mode=driving"),
        )
    }

    @Test
    fun pageAndFileLinksStayInside() {
        assertEquals(HtmlPreviewNavigation.Action.Stay, HtmlPreviewNavigation.classify("https://example.com/a"))
        assertEquals(HtmlPreviewNavigation.Action.Stay, HtmlPreviewNavigation.classify("file:///data/x.html"))
        assertEquals(HtmlPreviewNavigation.Action.Stay, HtmlPreviewNavigation.classify("about:blank"))
        assertEquals(HtmlPreviewNavigation.Action.Stay, HtmlPreviewNavigation.classify(null))
    }

    @Test
    fun scriptUrlsAreBlocked() {
        assertEquals(
            HtmlPreviewNavigation.Action.Block,
            HtmlPreviewNavigation.classify("javascript:alert(1)"),
        )
    }
}
