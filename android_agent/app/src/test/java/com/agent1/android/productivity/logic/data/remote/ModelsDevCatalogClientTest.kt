package com.agent1.android.productivity.logic.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsDevCatalogClientTest {

    @Test
    fun parseKeepsNewestTextModelsAndSkipsNonText() {
        val body = """
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
        """.trimIndent()

        val models = ModelsDevCatalogClient().parse(body, listOf("deepseek", "zai-coding-plan"))

        assertEquals(listOf("deepseek-flash", "deepseek-old"), models.map { it.id })
        assertEquals("Flash", models.first().name)
    }
}
