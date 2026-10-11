package com.agent1.android.productivity.logic.business

import android.content.Context
import androidx.annotation.StringRes
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.i18n.AppLocalization

/**
 * 把模型连接、网络和异常原文收成用户能看懂的一句。
 * 日志和 JSONL 仍保留原文；界面文案走 [R.string] / 系统语言。
 */
object UserFacingError {

    enum class Kind {
        GENERIC,
        NETWORK,
        TIMEOUT,
        OFFLINE,
        SSL,
        AUTH,
        SEARCH_AUTH,
        NO_KEY,
        FORBIDDEN,
        RATE,
        QUOTA,
        MODEL,
        SERVER,
        BAD_REQUEST,
        FILTER,
        PAGE,
    }

    private val cjk = Regex("[\\u4e00-\\u9fff]")
    private val technical = Regex(
        """SSE|Exception|DashScope|\bHTTP\s*/?\s*\d{3}\b|\bERR_|Caused by:|java\.|kotlin\.|android\.|""" +
            """Software caused|stream was reset|UnknownHost|SocketTimeout|Connection reset|""" +
            """Connection refused|unexpected end of stream|PKIX|SSLHandshake|NoSuchMethod|status=\d""",
        RegexOption.IGNORE_CASE,
    )

    fun explain(raw: String?): String = explain(AppLocalization.requireContext(), raw)

    fun explain(context: Context, raw: String?): String {
        val text = strip(raw)
        if (text.isEmpty()) return context.getString(R.string.error_generic)
        val kind = classifyKind(text)
        if (kind != null) return message(context, kind)
        return if (isFriendly(text)) text else context.getString(R.string.error_generic)
    }

    fun explainAssistantFailure(raw: String): String = explainAssistantFailure(AppLocalization.requireContext(), raw)

    fun explainAssistantFailure(context: Context, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("错误:") || trimmed.startsWith("Error:")) {
            return explain(context, trimmed)
        }
        if (trimmed.length <= 300) {
            classifyKind(strip(trimmed))?.let { return message(context, it) }
        }
        return raw
    }

    @StringRes
    fun stringRes(kind: Kind): Int = when (kind) {
        Kind.GENERIC -> R.string.error_generic
        Kind.NETWORK -> R.string.error_network
        Kind.TIMEOUT -> R.string.error_timeout
        Kind.OFFLINE -> R.string.error_offline
        Kind.SSL -> R.string.error_ssl
        Kind.AUTH -> R.string.error_auth
        Kind.SEARCH_AUTH -> R.string.error_search_auth
        Kind.NO_KEY -> R.string.error_no_key
        Kind.FORBIDDEN -> R.string.error_forbidden
        Kind.RATE -> R.string.error_rate
        Kind.QUOTA -> R.string.error_quota
        Kind.MODEL -> R.string.error_model
        Kind.SERVER -> R.string.error_server
        Kind.BAD_REQUEST -> R.string.error_bad_request
        Kind.FILTER -> R.string.error_filter
        Kind.PAGE -> R.string.error_page
    }

    fun message(context: Context, kind: Kind): String = context.getString(stringRes(kind))

    fun classifyKind(text: String?): Kind? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        if (lower.contains("未配置 api key")) return Kind.NO_KEY
        if (containsAny(lower, "datainspection", "data_inspection", "content filter", "inappropriate content")) {
            return Kind.FILTER
        }
        if (containsAny(lower, "arrearage", "insufficient_quota", "insufficient quota", "allocationquota")) {
            return Kind.QUOTA
        }
        if (isAuth(lower)) return if (lower.contains("tavily")) Kind.SEARCH_AUTH else Kind.AUTH
        if (isRateLimit(lower)) return Kind.RATE
        if (isModelMissing(lower)) return Kind.MODEL
        if (isForbidden(lower)) return Kind.FORBIDDEN
        if (isBadRequest(lower)) return Kind.BAD_REQUEST
        if (isServer(lower)) return Kind.SERVER
        if (isSsl(lower)) return Kind.SSL
        if (isTimeout(lower)) return Kind.TIMEOUT
        if (isOffline(lower)) return Kind.OFFLINE
        if (isDroppedStream(lower)) return Kind.NETWORK
        if (lower.contains("err_failed") || lower == "net::err_failed") return Kind.PAGE
        return null
    }

    private fun strip(raw: String?): String {
        return raw.orEmpty()
            .trim()
            .removePrefix("错误:")
            .removePrefix("错误：")
            .removePrefix("Error:")
            .trim()
    }

    private fun isFriendly(text: String): Boolean {
        if (technical.containsMatchIn(text)) return false
        if (cjk.containsMatchIn(text)) return true
        return text.length <= 280 && !text.contains("Exception", ignoreCase = true)
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
        return containsAny(
            lower,
            "http 500",
            "http 502",
            "http 503",
            "http 504",
            "status=500",
            "status=502",
            "status=503",
            "internalerror",
            "bad gateway",
            "service unavailable",
        )
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }
}
