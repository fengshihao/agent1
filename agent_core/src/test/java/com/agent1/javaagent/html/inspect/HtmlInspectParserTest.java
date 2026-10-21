package com.agent1.javaagent.html.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HtmlInspectParserTest {

    @Test
    void parse_readsFullPayload() {
        String json = """
            {
              "tag": "BUTTON",
              "id": "checkout-btn",
              "classes": ["checkout", "primary"],
              "textPreview": "立即支付",
              "outerHtml": "<button id=\\"checkout-btn\\">立即支付</button>",
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
            """;
        InspectedElement element = HtmlInspectParser.parse(json);
        assertNotNull(element);
        assertEquals("button", element.tag());
        assertEquals("checkout-btn", element.id());
        assertEquals(List.of("checkout", "primary"), element.classes());
        assertEquals("立即支付", element.textPreview());
        InspectRenderInfo render = element.render();
        assertNotNull(render);
        assertEquals("rgb(91, 98, 112)", render.color());
        assertEquals("rgb(250, 251, 252)", render.backgroundColor());
        assertEquals("11px", render.fontSize());
        assertEquals(302, render.width());
        assertEquals(18, render.height());
        assertEquals(2, element.ancestors().size());
        assertEquals("div", element.ancestors().get(0).tag());
        assertEquals("cart", element.ancestors().get(0).id());
        assertEquals(2, element.ancestors().get(0).nth());
    }

    @Test
    void parse_toleratesMissingRender() {
        InspectedElement element = HtmlInspectParser.parse("{\"tag\":\"div\"}");
        assertNotNull(element);
        assertNull(element.render());
    }

    @Test
    void parse_toleratesBrokenRenderFields() {
        InspectedElement element = HtmlInspectParser.parse(
            "{\"tag\":\"p\",\"render\":{\"color\":{\"r\":1},\"width\":-5,\"height\":\"x\"}}"
        );
        assertNotNull(element);
        InspectRenderInfo render = element.render();
        assertNotNull(render);
        assertEquals("", render.color());
        assertNull(render.width());
        assertNull(render.height());
    }

    @Test
    void parse_toleratesMissingOptionalFields() {
        InspectedElement element = HtmlInspectParser.parse("{\"tag\":\"div\"}");
        assertNotNull(element);
        assertEquals("div", element.tag());
        assertEquals("", element.id());
        assertTrue(element.classes().isEmpty());
        assertTrue(element.ancestors().isEmpty());
        assertEquals("", element.outerHtml());
    }

    @Test
    void parse_returnsNullOnBlankOrBrokenJson() {
        assertNull(HtmlInspectParser.parse(""));
        assertNull(HtmlInspectParser.parse("not json"));
        assertNull(HtmlInspectParser.parse("{\"id\":\"x\"}"));
        assertNull(HtmlInspectParser.parse("[1,2]"));
    }

    @Test
    void parse_skipsBrokenAncestorEntries() {
        String json = """
            {"tag":"span","ancestors":[
              {"tag":"main","nth":3},
              {"id":"no-tag"},
              {"tag":"","id":"broken"},
              null
            ]}
            """;
        InspectedElement element = HtmlInspectParser.parse(json);
        assertNotNull(element);
        assertEquals(1, element.ancestors().size());
        assertEquals("main", element.ancestors().get(0).tag());
        assertEquals(3, element.ancestors().get(0).nth());
    }

    @Test
    void parse_clampsInvalidNthToDefault() {
        InspectedElement element = HtmlInspectParser.parse("{\"tag\":\"p\",\"ancestors\":[{\"tag\":\"div\",\"nth\":0}]}");
        assertNotNull(element);
        assertEquals(1, element.ancestors().get(0).nth());
    }

    @Test
    void parse_dropsBlankClassEntries() {
        InspectedElement element = HtmlInspectParser.parse("{\"tag\":\"a\",\"classes\":[\"\", \"  \", \"nav-link\"]}");
        assertNotNull(element);
        assertEquals(List.of("nav-link"), element.classes());
    }
}
