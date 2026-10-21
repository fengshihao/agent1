package com.agent1.javaagent.html.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElementSelectorBuilderTest {

    private static InspectedElement el(
        String tag,
        String id,
        List<String> classes,
        List<InspectAncestor> ancestors
    ) {
        return new InspectedElement(tag, id, classes, "", "", null, ancestors);
    }

    @Test
    void prefersTargetId() {
        assertEquals("#checkout-btn", ElementSelectorBuilder.build(el("button", "checkout-btn", List.of(), List.of())));
    }

    @Test
    void prefersNearestAncestorIdOverClasses() {
        var selector = ElementSelectorBuilder.build(
            el("button", "", List.of("primary"), List.of(new InspectAncestor("div", "cart", List.of())))
        );
        assertEquals("#cart button", selector);
    }

    @Test
    void fallsBackToFirstClass() {
        var selector = ElementSelectorBuilder.build(el("a", "", List.of("nav-link", "item"), List.of()));
        assertEquals("a.nav-link", selector);
    }

    @Test
    void fallsBackToNthPathForPlainElements() {
        var element = new InspectedElement(
            "p",
            "",
            List.of(),
            "",
            "",
            null,
            List.of(
                new InspectAncestor("div", "", List.of(), 2),
                new InspectAncestor("section", "", List.of(), 1),
                new InspectAncestor("main", "", List.of(), 1),
                new InspectAncestor("body", "", List.of(), 1)
            )
        );
        assertEquals(
            "main:nth-of-type(1) section:nth-of-type(1) div:nth-of-type(2) p",
            ElementSelectorBuilder.build(element)
        );
    }

    @Test
    void loneElementWithoutAncestors() {
        assertEquals("h1", ElementSelectorBuilder.build(el("h1", "", List.of(), List.of())));
    }

    @Test
    void escapesSuspiciousIdCharacters() {
        String selector = ElementSelectorBuilder.build(el("div", "a:b::before", List.of(), List.of()));
        assertTrue(selector.startsWith("#a"));
        assertFalse(selector.contains(":"));
    }

    @Test
    void ancestorChainKeepsOrderNearToFar() {
        var selector = ElementSelectorBuilder.build(
            el(
                "span",
                "",
                List.of(),
                List.of(
                    new InspectAncestor("li", "", List.of(), 4),
                    new InspectAncestor("ul", "", List.of(), 2)
                )
            )
        );
        assertEquals("ul:nth-of-type(2) li:nth-of-type(4) span", selector);
    }
}
