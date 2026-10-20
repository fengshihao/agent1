package com.agent1.javaagent.modelcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelsDevCatalogClientTest {

    @Test
    void parseKeepsNewestTextModelsAndSkipsNonText() throws Exception {
        String body = """
            {
              "deepseek": {
                "models": {
                  "old": {
                    "id": "deepseek-old",
                    "name": "Old",
                    "release_date": "2025-01-01",
                    "modalities": { "output": ["text"] }
                  },
                  "new": {
                    "id": "deepseek-flash",
                    "name": "Flash",
                    "release_date": "2026-09-10",
                    "modalities": { "output": ["text"] }
                  },
                  "pic": {
                    "id": "image-only",
                    "name": "Pic",
                    "release_date": "2026-09-11",
                    "modalities": { "output": ["image"] }
                  }
                }
              },
              "zai-coding-plan": {
                "models": {
                  "dup": {
                    "id": "deepseek-flash",
                    "name": "Older copy",
                    "release_date": "2024-01-01",
                    "modalities": { "output": ["text"] }
                  }
                }
              }
            }
            """;

        List<ModelsDevCatalogClient.CatalogModel> models = new ModelsDevCatalogClient()
            .parse(body, List.of("deepseek", "zai-coding-plan"));

        assertEquals(List.of("deepseek-flash", "deepseek-old"), models.stream().map(m -> m.id()).toList());
        assertEquals("Flash", models.get(0).name());
    }
}
