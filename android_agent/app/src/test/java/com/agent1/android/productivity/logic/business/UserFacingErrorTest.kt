package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {

    @Test
    fun sseDropBecomesNetworkSentence() {
        assertEquals(
            UserFacingError.NETWORK,
            UserFacingError.explain("SSE failed: Software caused connection abort"),
        )
        assertEquals(
            UserFacingError.NETWORK,
            UserFacingError.explain("错误: SSE failed: unknown"),
        )
        assertEquals(
            UserFacingError.NETWORK,
            UserFacingError.explain("错误: java.net.SocketException: Connection reset"),
        )
    }

    @Test
    fun httpStatusBecomesActionableCopy() {
        assertEquals(UserFacingError.AUTH, UserFacingError.explain("SSE failed: HTTP 401 Unauthorized"))
        assertEquals(UserFacingError.SEARCH_AUTH, UserFacingError.explain("错误：Tavily HTTP 401"))
        assertEquals(UserFacingError.RATE, UserFacingError.explain("SSE failed: HTTP 429 Too Many Requests"))
        assertEquals(UserFacingError.SERVER, UserFacingError.explain("SSE failed: HTTP 503"))
        assertEquals(
            UserFacingError.AUTH,
            UserFacingError.explain("DashScope error [InvalidApiKey]: Invalid API-key provided."),
        )
        assertEquals(UserFacingError.QUOTA, UserFacingError.explain("DashScope error [Arrearage]: overdue"))
        assertEquals(UserFacingError.RATE, UserFacingError.explain("DashScope error [Throttling.RateQuota]"))
        assertEquals(UserFacingError.FILTER, UserFacingError.explain("DashScope error [DataInspectionFailed]"))
        assertEquals(UserFacingError.MODEL, UserFacingError.explain("model_not_found: qwen-nope"))
    }

    @Test
    fun connectionKindsStayDistinct() {
        assertEquals(UserFacingError.TIMEOUT, UserFacingError.explain("SocketTimeoutException: timeout"))
        assertEquals(UserFacingError.OFFLINE, UserFacingError.explain("UnknownHostException"))
        assertEquals(UserFacingError.OFFLINE, UserFacingError.explain("net::ERR_INTERNET_DISCONNECTED"))
        assertEquals(UserFacingError.SSL, UserFacingError.explain("PKIX path building failed"))
        assertEquals(UserFacingError.SSL, UserFacingError.explain("net::ERR_SSL_PROTOCOL_ERROR"))
        assertEquals(UserFacingError.PAGE, UserFacingError.explain("net::ERR_FAILED"))
        assertEquals(UserFacingError.NO_KEY, UserFacingError.explain("未配置 API Key（请在 App「模型配置」中填写）"))
    }

    @Test
    fun friendlyChineseAndNormalRepliesStay() {
        assertEquals("文件不存在: notes.md", UserFacingError.explain("错误：文件不存在: notes.md"))
        assertEquals("参数 timeout 不能为空", UserFacingError.explain("错误：参数 timeout 不能为空"))
        assertEquals(
            "名称只能包含字母、数字、下划线和连字符",
            UserFacingError.explain("名称只能包含字母、数字、下划线和连字符"),
        )
        assertEquals(
            "没有可落盘的返回值:脚本返回了 null 或 undefined。",
            UserFacingError.explain("没有可落盘的返回值:脚本返回了 null 或 undefined。"),
        )
        val reply = "我先说明火箭的推进剂在燃烧室混合。"
        assertEquals(reply, UserFacingError.explainAssistantFailure(reply))
        assertEquals(UserFacingError.NETWORK, UserFacingError.explainAssistantFailure("错误: SSE failed: stream was reset: CANCEL"))
    }

    @Test
    fun longReplyIsNotRewrittenBecauseItMentionsAnErrorWord() {
        val reply = "下面是排查记录。\n" + "甲".repeat(280) + "\nSSE failed: connection abort"
        assertEquals(reply, UserFacingError.explainAssistantFailure(reply))
    }

    @Test
    fun bareExceptionBecomesGeneric() {
        assertEquals(UserFacingError.GENERIC, UserFacingError.explain("IllegalStateException"))
        assertEquals(UserFacingError.GENERIC, UserFacingError.explain(null))
        assertEquals(UserFacingError.GENERIC, UserFacingError.explain("  "))
        assertTrue(UserFacingError.explain("java.lang.IllegalStateException: boom").startsWith("出了点问题"))
    }
}
