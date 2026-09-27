package com.agent1.javaagent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CatalogIndexTest {

    @Test
    void parsesItems() throws Exception {
        String json = """
            {
              "schemaVersion": 1,
              "catalogId": "demo",
              "baseUrl": "https://cdn.example/v1/",
              "items": [
                {
                  "id": "script.a",
                  "kind": "script",
                  "version": "2",
                  "digest": "sha256:abc",
                  "path": "scripts/a.js"
                }
              ]
            }
            """;
        CatalogIndex index = CatalogIndex.parse(json);
        assertEquals("demo", index.catalogId());
        assertEquals(1, index.items().size());
        assertEquals("script.a", index.items().get(0).id());
    }

    @Test
    void rejectsMissingBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> CatalogIndex.parse("""
            {"schemaVersion":1,"items":[]}
            """));
    }
}
