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
              "render": {
                "color": "rgb(91, 98, 112)",
                "backgroundColor": "rgb(250, 251, 252)",
                "fontSize": "11px",
                "display": "block",
                "width": 302,
                "height": 18
              },
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
        val render = checkNotNull(element.render)
        assertEquals("rgb(91, 98, 112)", render.color)
        assertEquals("rgb(250, 251, 252)", render.backgroundColor)
        assertEquals("11px", render.fontSize)
        assertEquals(302, render.width)
        assertEquals(18, render.height)
        assertEquals(2, element.ancestors.size)
        assertEquals("div", element.ancestors[0].tag)
        assertEquals("cart", element.ancestors[0].id)
        assertEquals(2, element.ancestors[0].nth)
    }

    @Test
    fun parse_toleratesMissingRender() {
        val element = checkNotNull(HtmlInspectParser.parse("""{"tag":"div"}"""))
        assertEquals(null, element.render)
    }

    @Test
    fun parse_toleratesBrokenRenderFields() {
        // render 字段类型损坏/负尺寸时降级为默认值，不影响整体解析。
        // 注意：Android org.json 的 optString 会把标量强转（123 → "123"），
        // 仅结构体类型（对象/数组）与负数/非法尺寸回落到默认值。
        val element = checkNotNull(
            HtmlInspectParser.parse(
                """{"tag":"p","render":{"color":{"r":1},"width":-5,"height":"x"}}""",
            ),
        )
        val render = checkNotNull(element.render)
        assertEquals("", render.color)
        assertEquals(null, render.width)
        assertEquals(null, render.height)
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