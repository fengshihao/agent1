package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlInspectParserTest {

    @Test
    fun parse_readsFullPayload() {
        val json = """
            {
              "tag": "BUTTON",
              "id": "checkout-btn",
              "classes": ["checkout", "primary"],
              "textPreview": "立即支付",
              "outerHtml": "<button id=\"checkout-btn\">立即支付</button>",
              "ancestors": [
                {"tag": "DIV", "id": "cart", "classes": ["card"], "nth": 2},
                {"tag": "SECTION", "id": "", "classes": [], "nth": 1}
              ]
            }
        """.trimIndent()
        val element = checkNotNull(HtmlInspectParser.parse(json))
        assertEquals("button", element.tag)
        assertEquals("checkout-btn", element.id)
        assertEquals(listOf("checkout", "primary"), element.classes)
        assertEquals("立即支付", element.textPreview)
        assertEquals(2, element.ancestors.size)
        assertEquals("div", element.ancestors[0].tag)
        assertEquals("cart", element.ancestors[0].id)
        assertEquals(2, element.ancestors[0].nth)
    }

    @Test
    fun parse_toleratesMissingOptionalFields() {
        val element = checkNotNull(HtmlInspectParser.parse("""{"tag":"div"}"""))
        assertEquals("div", element.tag)
        assertEquals("", element.id)
        assertTrue(element.classes.isEmpty())
        assertTrue(element.ancestors.isEmpty())
        assertEquals("", element.outerHtml)
    }

    @Test
    fun parse_returnsNullOnBlankOrBrokenJson() {
        assertNull(HtmlInspectParser.parse(""))
        assertNull(HtmlInspectParser.parse("not json"))
        assertNull(HtmlInspectParser.parse("""{"id":"x"}"""))
        assertNull(HtmlInspectParser.parse("""[1,2]"""))
    }

    @Test
    fun parse_skipsBrokenAncestorEntries() {
        val json = """
            {"tag":"span","ancestors":[
              {"tag":"main","nth":3},
              {"id":"no-tag"},
              {"tag":"","id":"broken"},
              null
            ]}
        """.trimIndent()
        val element = checkNotNull(HtmlInspectParser.parse(json))
        assertEquals(1, element.ancestors.size)
        assertEquals("main", element.ancestors[0].tag)
        assertEquals(3, element.ancestors[0].nth)
    }

    @Test
    fun parse_clampsInvalidNthToDefault() {
        val element = checkNotNull(HtmlInspectParser.parse("""{"tag":"p","ancestors":[{"tag":"div","nth":0}]}"""))
        assertEquals(1, element.ancestors[0].nth)
    }

    @Test
    fun parse_dropsBlankClassEntries() {
        val element = checkNotNull(
            HtmlInspectParser.parse("""{"tag":"a","classes":["", "  ", "nav-link"]}"""),
        )
        assertEquals(listOf("nav-link"), element.classes)
        assertNotNull(element)
    }
}