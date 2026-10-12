package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementSelectorBuilderTest {

    private fun el(
        tag: String,
        id: String = "",
        classes: List<String> = emptyList(),
        ancestors: List<InspectAncestor> = emptyList(),
    ) = InspectedElement(tag = tag, id = id, classes = classes, ancestors = ancestors)

    @Test
    fun prefersTargetId() {
        val selector = ElementSelectorBuilder.build(el("button", id = "checkout-btn"))
        assertEquals("#checkout-btn", selector)
    }

    @Test
    fun prefersNearestAncestorIdOverClasses() {
        val selector = ElementSelectorBuilder.build(
            el("button", classes = listOf("primary"), ancestors = listOf(InspectAncestor("div", id = "cart"))),
        )
        assertEquals("#cart button", selector)
    }

    @Test
    fun fallsBackToFirstClass() {
        val selector = ElementSelectorBuilder.build(
            el("a", classes = listOf("nav-link", "item")),
        )
        assertEquals("a.nav-link", selector)
    }

    @Test
    fun fallsBackToNthPathForPlainElements() {
        val element = InspectedElement(
            tag = "p",
            ancestors = listOf(
                InspectAncestor("div", nth = 2),
                InspectAncestor("section", nth = 1),
                InspectAncestor("main", nth = 1),
                InspectAncestor("body", nth = 1),
            ),
        )
        // 只保留最近 3 层祖先：div(2) section(1) main(1)，远→近拼接
        assertEquals("main:nth-of-type(1) section:nth-of-type(1) div:nth-of-type(2) p", ElementSelectorBuilder.build(element))
    }

    @Test
    fun loneElementWithoutAncestors() {
        assertEquals("h1", ElementSelectorBuilder.build(el("h1")))
    }

    @Test
    fun escapesSuspiciousIdCharacters() {
        val selector = ElementSelectorBuilder.build(el("div", id = "a:b::before"))
        // 冒号等字符被剔除，避免伪选择符注入
        assertTrue(selector.startsWith("#a"))
        assertTrue(!selector.contains(":"))
    }

    @Test
    fun ancestorChainKeepsOrderNearToFar() {
        val selector = ElementSelectorBuilder.build(
            el(
                "span",
                ancestors = listOf(
                    InspectAncestor("li", nth = 4),
                    InspectAncestor("ul", nth = 2),
                ),
            ),
        )
        assertEquals("ul:nth-of-type(2) li:nth-of-type(4) span", selector)
    }
}