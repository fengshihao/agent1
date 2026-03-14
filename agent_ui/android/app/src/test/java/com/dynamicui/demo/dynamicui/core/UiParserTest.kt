package com.dynamicui.demo.dynamicui.core

import com.dynamicui.demo.llm.cleanModelJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiParserTest {

    private val parser = UiParser()

    @Test
    fun `parse valid payload should succeed`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "column",
                "children": [
                  { "type": "text", "content": "hello" },
                  {
                    "type": "button",
                    "text": "go",
                    "action": {
                      "type": "navigate",
                      "route": "detail",
                      "params": {"id": "1"}
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `button navigate action without route should fail`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "button",
                "text": "go",
                "action": { "type": "navigate" }
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `parse form payload with input select date and time should succeed`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "column",
                "children": [
                  { "type": "input", "label": "姓名", "placeholder": "请输入姓名" },
                  {
                    "type": "select",
                    "label": "城市",
                    "selectedValue": "shanghai",
                    "options": [
                      { "label": "北京", "value": "beijing" },
                      { "label": "上海", "value": "shanghai" }
                    ]
                  },
                  { "type": "date_picker", "label": "日期", "value": "2026-03-13" },
                  { "type": "time_picker", "label": "时间", "value": "09:30" }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `select without options should fail`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "select",
                "label": "城市"
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `date and time with invalid format should fail`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "column",
                "children": [
                  { "type": "date_picker", "value": "2026/03/13" },
                  { "type": "time_picker", "value": "9:3" }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `llm wrapped json should parse after cleaning`() {
        val wrapped = """
            这是我生成的 UI:
            ```json
            {
              "version": "1.0",
              "root": { "type": "text", "content": "hello from llm" }
            }
            ```
        """.trimIndent()

        val cleaned = cleanModelJson(wrapped)
        val result = parser.parse(cleaned)
        assertTrue(result.isSuccess)
    }
}
