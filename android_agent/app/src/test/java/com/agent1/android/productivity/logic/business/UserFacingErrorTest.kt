package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {

    @Test
    fun sseDropBecomesNetworkKind() {
        assertEquals(
            UserFacingError.Kind.NETWORK,
            UserFacingError.classifyKind("SSE failed: Software caused connection abort"),
        )
        assertEquals(
            UserFacingError.Kind.NETWORK,
            UserFacingError.classifyKind("错误: SSE failed: unknown"),
        )
        assertEquals(
            UserFacingError.Kind.NETWORK,
            UserFacingError.classifyKind("错误: java.net.SocketException: Connection reset"),
        )
    }

    @Test
    fun httpStatusBecomesActionableKind() {
        assertEquals(UserFacingError.Kind.AUTH, UserFacingError.classifyKind("SSE failed: HTTP 401 Unauthorized"))
        assertEquals(UserFacingError.Kind.SEARCH_AUTH, UserFacingError.classifyKind("错误：Tavily HTTP 401"))
        assertEquals(UserFacingError.Kind.RATE, UserFacingError.classifyKind("SSE failed: HTTP 429 Too Many Requests"))
        assertEquals(UserFacingError.Kind.SERVER, UserFacingError.classifyKind("SSE failed: HTTP 503"))
        assertEquals(
            UserFacingError.Kind.AUTH,
            UserFacingError.classifyKind("DashScope error [InvalidApiKey]: Invalid API-key provided."),
        )
        assertEquals(UserFacingError.Kind.QUOTA, UserFacingError.classifyKind("DashScope error [Arrearage]: overdue"))
        assertEquals(UserFacingError.Kind.RATE, UserFacingError.classifyKind("DashScope error [Throttling.RateQuota]"))
        assertEquals(UserFacingError.Kind.FILTER, UserFacingError.classifyKind("DashScope error [DataInspectionFailed]"))
        assertEquals(UserFacingError.Kind.MODEL, UserFacingError.classifyKind("model_not_found: qwen-nope"))
    }

    @Test
    fun connectionKindsStayDistinct() {
        assertEquals(UserFacingError.Kind.TIMEOUT, UserFacingError.classifyKind("SocketTimeoutException: timeout"))
        assertEquals(UserFacingError.Kind.OFFLINE, UserFacingError.classifyKind("UnknownHostException"))
        assertEquals(UserFacingError.Kind.OFFLINE, UserFacingError.classifyKind("net::ERR_INTERNET_DISCONNECTED"))
        assertEquals(UserFacingError.Kind.SSL, UserFacingError.classifyKind("PKIX path building failed"))
        assertEquals(UserFacingError.Kind.SSL, UserFacingError.classifyKind("net::ERR_SSL_PROTOCOL_ERROR"))
        assertEquals(UserFacingError.Kind.PAGE, UserFacingError.classifyKind("net::ERR_FAILED"))
        assertEquals(
            UserFacingError.Kind.NO_KEY,
            UserFacingError.classifyKind("未配置 API Key（请在 App「模型配置」中填写）"),
        )
    }

    @Test
    fun friendlyChineseIsNotClassified() {
        assertNull(UserFacingError.classifyKind("文件不存在: notes.md"))
        assertNull(UserFacingError.classifyKind("名称只能包含字母、数字、下划线和连字符"))
    }

    @Test
    fun bareExceptionHasNoKind() {
        assertNull(UserFacingError.classifyKind("IllegalStateException"))
        assertNull(UserFacingError.classifyKind(null))
        assertNull(UserFacingError.classifyKind("  "))
        assertTrue(UserFacingError.classifyKind("java.lang.IllegalStateException: boom") == null)
    }
}
