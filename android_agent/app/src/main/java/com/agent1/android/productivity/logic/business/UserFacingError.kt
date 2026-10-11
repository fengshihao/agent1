package com.agent1.android.productivity.logic.business

/**
 * 把模型连接、网络和异常原文收成普通用户能看懂的一句中文。
 * 日志和 JSONL 仍保留原文；这里只负责界面上的那句话。
 */
object UserFacingError {

    const val GENERIC = "出了点问题，请再试一次。"
    const val NETWORK = "网络中断了，这次回复没有传完。请再发一次。"
    const val TIMEOUT = "等得太久，没有收到回复。请再试一次。"
    const val OFFLINE = "连不上网络。请检查网络后再试。"
    const val SSL = "安全连接失败。请检查网络，并确认手机时间是否正确。"
    const val AUTH = "模型密钥无效或已过期。请打开「模型」，重新填写 API Key。"
    const val SEARCH_AUTH = "联网搜索的密钥无效。请打开「模型」，检查搜索密钥。"
    const val NO_KEY = "还没有填写模型密钥。请打开「模型」，填上 API Key。"
    const val FORBIDDEN = "没有权限使用这个模型。请检查密钥和账号。"
    const val RATE = "请求太频繁，请稍等一会儿再试。"
    const val QUOTA = "模型额度不够，或账号欠费。请到服务商处检查余额。"
    const val MODEL = "找不到当前模型。请打开「模型」，重新选一个。"
    const val SERVER = "模型服务暂时不可用，请过一会儿再试。"
    const val BAD_REQUEST = "这次请求没有被接受。请检查「模型」里的名称是否正确，或换种说法再试。"
    const val FILTER = "这段内容没有通过安全检查，换种说法再试。"
    const val PAGE = "页面没有打开。请再试一次。"

    private val cjk = Regex("[\\u4e00-\\u9fff]")
    private val technical = Regex(
        """SSE|Exception|DashScope|\bHTTP\s*/?\s*\d{3}\b|\bERR_|Caused by:|java\.|kotlin\.|android\.|""" +
            """Software caused|stream was reset|UnknownHost|SocketTimeout|Connection reset|""" +
            """Connection refused|unexpected end of stream|PKIX|SSLHandshake|NoSuchMethod|status=\d""",
        RegexOption.IGNORE_CASE,
    )

    fun explain(raw: String?): String {
        val text = strip(raw)
        if (text.isEmpty()) return GENERIC
        return classify(text) ?: if (isFriendly(text)) text else GENERIC
    }

    /**
     * 助手气泡里的正文。只有整段就是失败汇报时才改写，避免把正常回复里偶然出现的英文吞掉。
     */
    fun explainAssistantFailure(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("错误:")) return explain(trimmed)
        if (trimmed.length <= 300) {
            classify(strip(trimmed))?.let { return it }
        }
        return raw
    }

    private fun strip(raw: String?): String {
        return raw.orEmpty()
            .trim()
            .removePrefix("错误:")
            .removePrefix("错误：")
            .trim()
    }

    private fun isFriendly(text: String): Boolean {
        return cjk.containsMatchIn(text) && !technical.containsMatchIn(text)
    }

    private fun classify(text: String): String? {
        val lower = text.lowercase()
        if (lower.contains("未配置 api key")) return NO_KEY
        if (containsAny(lower, "datainspection", "data_inspection", "content filter", "inappropriate content")) {
            return FILTER
        }
        if (containsAny(lower, "arrearage", "insufficient_quota", "insufficient quota", "allocationquota")) {
            return QUOTA
        }
        if (isAuth(lower)) return if (lower.contains("tavily")) SEARCH_AUTH else AUTH
        if (isRateLimit(lower)) return RATE
        if (isModelMissing(lower)) return MODEL
        if (isForbidden(lower)) return FORBIDDEN
        if (isBadRequest(lower)) return BAD_REQUEST
        if (isServer(lower)) return SERVER
        if (isSsl(lower)) return SSL
        if (isTimeout(lower)) return TIMEOUT
        if (isOffline(lower)) return OFFLINE
        if (isDroppedStream(lower)) return NETWORK
        if (lower.contains("err_failed") || lower == "net::err_failed") return PAGE
        return null
    }

    private fun isDroppedStream(lower: String): Boolean {
        return containsAny(
            lower,
            "sse failed",
            "software caused connection abort",
            "connection abort",
            "connection reset",
            "connection reset by peer",
            "stream was reset",
            "unexpected end of stream",
            "socket closed",
            "broken pipe",
            "econnreset",
            "eofexception",
            "socketexception",
            "err_connection_aborted",
            "err_connection_reset",
            "err_connection_closed",
            "err_empty_response",
        )
    }

    private fun isTimeout(lower: String): Boolean {
        return containsAny(lower, "sockettimeout", "timed out", "err_timed_out", "err_connection_timed_out")
    }

    private fun isOffline(lower: String): Boolean {
        return containsAny(
            lower,
            "unknownhost",
            "unable to resolve",
            "no address associated",
            "network is unreachable",
            "failed to connect",
            "connection refused",
            "connectexception",
            "err_internet_disconnected",
            "err_name_not_resolved",
            "err_address_unreachable",
            "err_connection_refused",
            "err_network_changed",
        )
    }

    private fun isSsl(lower: String): Boolean {
        return containsAny(lower, "sslhandshake", "certpath", "pkix", "certificate", "err_cert", "err_ssl")
    }

    private fun isAuth(lower: String): Boolean {
        return containsAny(
            lower,
            "invalidapikey",
            "invalid api-key",
            "invalid api key",
            "incorrect api key",
            "unauthorized",
            "http 401",
            "http/1.1 401",
            "status=401",
            "status 401",
        )
    }

    private fun isForbidden(lower: String): Boolean {
        return containsAny(lower, "accessdenied", "http 403", "status=403", "status 403")
    }

    private fun isRateLimit(lower: String): Boolean {
        return containsAny(lower, "throttl", "ratequota", "rate limit", "too many requests", "http 429", "status=429")
    }

    private fun isModelMissing(lower: String): Boolean {
        return containsAny(lower, "model not found", "model_not_found", "model.notfound", "unknown model")
            || (lower.contains("404") && lower.contains("model"))
    }

    private fun isBadRequest(lower: String): Boolean {
        return containsAny(lower, "badrequest", "invalidparameter", "http 400", "status=400")
    }

    private fun isServer(lower: String): Boolean {
        return containsAny(lower, "http 500", "http 502", "http 503", "http 504", "status=500", "status=502", "status=503", "internalerror", "bad gateway", "service unavailable")
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }
}
