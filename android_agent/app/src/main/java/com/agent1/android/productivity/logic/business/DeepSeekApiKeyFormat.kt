package com.agent1.android.productivity.logic.business

/** 只做形态检查，不把密钥打进日志。 */
object DeepSeekApiKeyFormat {

    fun normalize(raw: String): String = raw.trim()

    fun isPlausible(raw: String): Boolean {
        val key = normalize(raw)
        if (key.length !in 16..256) return false
        return key.startsWith("sk-") || key.startsWith("sk_")
    }
}
