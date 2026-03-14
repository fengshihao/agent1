package com.dynamicui.demo.llm

import com.dynamicui.demo.dynamicui.core.UiParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenClientTest {
    private val testJson = Json { explicitNulls = false }

    @Test
    fun `cleanModelJson should remove code fences and extra text`() {
        val raw = """
            这是生成结果:
            ```json
            {"version":"1.0","root":{"type":"text","content":"hello"}}
            ```
            请使用以上 JSON。
        """.trimIndent()

        val cleaned = cleanModelJson(raw)
        assertEquals("""{"version":"1.0","root":{"type":"text","content":"hello"}}""", cleaned)
    }

    @Test
    fun `qwen client should parse message content`() = runBlocking {
        val transport = FakeTransport(
            code = 200,
            body = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "```json\n{\"version\":\"1.0\",\"root\":{\"type\":\"text\",\"content\":\"ok\"}}\n```"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        val client = QwenClient(
            config = QwenClientConfig(apiKey = "test-key"),
            transport = transport
        )
        val agent = LlmUiAgent(
            qwenClient = client,
            prompts = LlmPromptBundle(
                generationSystemPrompt = "只返回 JSON",
                summarySystemPrompt = "输出总结"
            )
        )

        val result = agent.generateUiJson("生成一个简单页面")
        assertTrue(result.isSuccess)
        assertEquals(
            """{"version":"1.0","root":{"type":"text","content":"ok"}}""",
            result.getOrThrow()
        )
    }

    @Test
    fun `qwen client should return failure when http error`() = runBlocking {
        val transport = FakeTransport(
            code = 429,
            body = """{"error":{"message":"too many requests"}}"""
        )
        val client = QwenClient(
            config = QwenClientConfig(apiKey = "test-key"),
            transport = transport
        )

        val result = client.chat("system", "user")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("429") == true)
    }

    @Test
    fun `generation then repair should produce parseable ui document`() = runBlocking {
        val transport = QueueTransport(
            responses = ArrayDeque(
                listOf(
                    HttpResult(
                        code = 200,
                        body = chatResponse(
                            // 首次输出故意缺少 route，触发 parser 校验失败
                            """
                            {
                              "version":"1.0",
                              "root":{
                                "type":"button",
                                "text":"提交",
                                "action":{"type":"navigate"}
                              }
                            }
                            """.trimIndent()
                        )
                    ),
                    HttpResult(
                        code = 200,
                        body = chatResponse(
                            """
                            {
                              "version":"1.0",
                              "root":{
                                "type":"button",
                                "text":"提交",
                                "action":{"type":"navigate","route":"submit"}
                              }
                            }
                            """.trimIndent()
                        )
                    )
                )
            )
        )

        val client = QwenClient(
            config = QwenClientConfig(apiKey = "test-key"),
            transport = transport
        )
        val agent = LlmUiAgent(
            qwenClient = client,
            prompts = LlmPromptBundle(
                generationSystemPrompt = "只返回 JSON",
                summarySystemPrompt = "输出总结"
            )
        )
        val parser = UiParser()

        val first = agent.generateUiJson("生成提交按钮")
        assertTrue(first.isSuccess)
        val firstParse = parser.parse(first.getOrThrow())
        assertTrue(firstParse.isSuccess.not())

        val repaired = agent.repairUiJson(
            intent = "生成提交按钮",
            badJson = first.getOrThrow(),
            parserErrors = firstParse.errors
        )
        assertTrue(repaired.isSuccess)
        val repairedParse = parser.parse(repaired.getOrThrow())
        assertTrue(repairedParse.isSuccess)
    }

    @Test
    fun `summarizeSelection should return model summary text`() = runBlocking {
        val transport = QueueTransport(
            responses = ArrayDeque(
                listOf(
                    HttpResult(
                        code = 200,
                        body = chatResponse("用户已填写姓名与城市，建议补充日期后提交。")
                    )
                )
            )
        )

        val client = QwenClient(
            config = QwenClientConfig(apiKey = "test-key"),
            transport = transport
        )
        val agent = LlmUiAgent(
            qwenClient = client,
            prompts = LlmPromptBundle(
                generationSystemPrompt = "只返回 JSON",
                summarySystemPrompt = "输出总结"
            )
        )

        val result = agent.summarizeSelection(
            intent = "生成报名表",
            generatedUiJson = """{"version":"1.0","root":{"type":"text","content":"demo"}}""",
            selection = mapOf("name" to "张三", "city" to "beijing")
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("建议补充日期"))
    }

    private fun chatResponse(content: String): String {
        val encoded = testJson.encodeToString(content)
        return """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": $encoded
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private class FakeTransport(
        private val code: Int,
        private val body: String
    ) : HttpTransport {
        override suspend fun postJson(
            url: String,
            headers: Map<String, String>,
            body: String
        ): HttpResult {
            return HttpResult(code = code, body = this.body)
        }
    }

    private class QueueTransport(
        private val responses: ArrayDeque<HttpResult>
    ) : HttpTransport {
        override suspend fun postJson(
            url: String,
            headers: Map<String, String>,
            body: String
        ): HttpResult {
            return responses.removeFirstOrNull()
                ?: error("No more queued responses")
        }
    }
}
