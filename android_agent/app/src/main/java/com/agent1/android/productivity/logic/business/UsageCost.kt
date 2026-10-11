package com.agent1.android.productivity.logic.business

import java.util.Locale

/**
 * 把累计 token 折成人民币参考费用。单价是每 100 万 token。
 *
 * 只看**当前模型**的标价，历史用量里换过的模型、闲时/忙时都不分开算。
 * 所以结果不是账单，只是让用户对费用有个量级。DeepSeek 取高峰时段价，因为价目经常改。
 *
 * 标价来源（2026-10 查阅的公开价目，会过期）：
 * - DeepSeek 用中文文档的人民币价，不把美元价再乘汇率。
 *   见 https://api-docs.deepseek.com/zh-cn/quick_start/pricing/
 *   高峰时段，每百万 token：Flash 未命中 2 元、缓存命中 0.04 元、输出 8 元；
 *   V4 Pro 未命中 9 元、缓存命中 0.3 元、输出 27 元。
 *   空闲大约是高峰的一半，这里不按空闲计。
 * - Qwen3.7 Flash 中国站 32K 以内档已是人民币：输入 ¥0.20、输出 ¥0.80。
 *   缓存没有单独的人民币标价，按公开的隐式缓存与输入价比例，取输入价的约 1/5（¥0.04）。
 *   只有国际站地址仍是美元标价，这时才乘 [USD_TO_CNY]。
 */
data class UsageCostLine(
    val label: String,
    val detail: String,
    val moneyText: String,
)

data class UsageCostEstimate(
    /** 抽屉底部一行，例如「约 ¥5.33」。没有标价时退回 token 量。 */
    val compact: String,
    /** 页面上最大的那一行。 */
    val headline: String,
    val caption: String,
    val lines: List<UsageCostLine>,
    val caveat: String,
    val hasCost: Boolean,
)

/**
 * 仅用于公开价目本身是美元的模型（目前只有 Qwen 国际站）。
 * DeepSeek 中文文档已经是人民币，不能再乘这个数。
 * 取 2026-10 附近的大致中间价，不是实时牌价。
 */
internal const val USD_TO_CNY = 7.1

private data class TokenListPrice(
    val label: String,
    /** 公开标价的原始货币。展示前若是 USD，会乘 [USD_TO_CNY]。 */
    val sourceCurrency: String,
    val inputMissPerMillion: Double,
    val inputHitPerMillion: Double,
    val outputPerMillion: Double,
) {
    fun missCny(): Double = toCny(inputMissPerMillion)
    fun hitCny(): Double = toCny(inputHitPerMillion)
    fun outputCny(): Double = toCny(outputPerMillion)

    private fun toCny(amount: Double): Double =
        if (sourceCurrency == "USD") amount * USD_TO_CNY else amount
}

object UsageCost {

    fun estimate(
        usage: UsageOverview,
        currentModelId: String = "",
        baseUrl: String = "",
    ): UsageCostEstimate {
        if (!usage.hasUsage) {
            return UsageCostEstimate(
                compact = "尚无用量",
                headline = "尚无用量",
                caption = "开始对话后，这里按当前模型的标价估一个人民币参考值。",
                lines = emptyList(),
                caveat = "",
                hasCost = false,
            )
        }
        // 故意不用日志里各次 run 的模型。用户可能换过模型，闲时/忙时也不一样，
        // 全部按现在设置里的这一个模型计价，避免假装算得很准。
        val price = listPrice(currentModelId, baseUrl)
        if (price == null) {
            val amount = formatMillions(usage.totalTokens)
            return UsageCostEstimate(
                compact = amount,
                headline = amount,
                caption = "当前模型没有收录标价，这里只显示 token 数量。",
                lines = tokenLines(usage),
                caveat = REFERENCE_NOTE,
                hasCost = false,
            )
        }
        val missTokens = (usage.inputTokens - usage.cachedTokens).coerceAtLeast(0L)
        val hitTokens = usage.cachedTokens.coerceAtMost(usage.inputTokens)
        val lines = mutableListOf<UsageCostLine>()
        var total = 0.0
        if (missTokens > 0L) {
            val cost = costOf(missTokens, price.missCny())
            total += cost
            lines += UsageCostLine(
                label = "输入",
                detail = "${formatMillions(missTokens)} × ${rate(price.missCny())}",
                moneyText = formatCny(cost),
            )
        }
        if (hitTokens > 0L) {
            val cost = costOf(hitTokens, price.hitCny())
            total += cost
            lines += UsageCostLine(
                label = "缓存命中",
                detail = "${formatMillions(hitTokens)} × ${rate(price.hitCny())}",
                moneyText = formatCny(cost),
            )
        }
        if (usage.outputTokens > 0L) {
            val cost = costOf(usage.outputTokens, price.outputCny())
            total += cost
            lines += UsageCostLine(
                label = "输出",
                detail = "${formatMillions(usage.outputTokens)} × ${rate(price.outputCny())}",
                moneyText = formatCny(cost),
            )
        }
        val fxNote = when {
            price.label.startsWith("DeepSeek") ->
                "DeepSeek 按中文文档的高峰时段人民币标价估算，空闲大约是一半。官网价格会调整。"
            price.sourceCurrency == "USD" ->
                "这份标价原是美元，按 1 美元 ≈ $USD_TO_CNY 元折成人民币，不是实时汇率。"
            else -> ""
        }
        return UsageCostEstimate(
            compact = "约 ${formatCny(total)}",
            headline = "约 ${formatCny(total)}",
            caption = if (price.label.startsWith("DeepSeek")) {
                "${price.label} · 每百万 token · 按高峰时段"
            } else {
                "${price.label} · 每百万 token · 仅供参考"
            },
            lines = lines,
            caveat = REFERENCE_NOTE + fxNote,
            hasCost = true,
        )
    }

    /** 给费用页用的数量：不足一万仍用个数，再往上用「万 / 百万」。 */
    fun formatMillions(tokens: Long): String {
        if (tokens <= 0L) return "0"
        if (tokens < 10_000L) return "%,d".format(Locale.US, tokens)
        if (tokens < 1_000_000L) return formatScaled(tokens / 10_000.0, "万")
        return formatScaled(tokens / 1_000_000.0, "百万")
    }

    private fun tokenLines(usage: UsageOverview): List<UsageCostLine> {
        val lines = mutableListOf<UsageCostLine>()
        val miss = (usage.inputTokens - usage.cachedTokens).coerceAtLeast(0L)
        val hit = usage.cachedTokens.coerceAtMost(usage.inputTokens)
        if (miss > 0L) lines += UsageCostLine("输入", formatMillions(miss), "")
        if (hit > 0L) lines += UsageCostLine("缓存命中", formatMillions(hit), "")
        if (usage.outputTokens > 0L) lines += UsageCostLine("输出", formatMillions(usage.outputTokens), "")
        return lines
    }

    private fun costOf(tokens: Long, cnyPerMillion: Double): Double = tokens / 1_000_000.0 * cnyPerMillion

    private fun rate(cnyPerMillion: Double): String = "${formatCny(cnyPerMillion, rate = true)}/百万"

    private fun formatCny(amount: Double, rate: Boolean = false): String {
        if (amount <= 0.0) return "¥0"
        if (!rate && amount < 0.01) return "< ¥0.01"
        val digits = if (rate && amount < 1.0) 3 else 2
        val text = "%.${digits}f".format(Locale.US, amount).let { raw ->
            if (rate) raw.trimEnd('0').trimEnd('.') else raw
        }
        return "¥$text"
    }

    private fun formatScaled(value: Double, unit: String): String {
        val text = when {
            value >= 100.0 -> "%.0f".format(Locale.US, value)
            value >= 10.0 -> "%.1f".format(Locale.US, value).trimEnd('0').trimEnd('.')
            else -> "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')
        }
        return "$text $unit"
    }

    private fun listPrice(modelId: String, baseUrl: String): TokenListPrice? {
        val id = modelId.trim().lowercase()
        if (id.isEmpty()) return null
        if (id == "deepseek-flash" || id.startsWith("deepseek-v4-flash") || id.startsWith("deepseek-flash")) {
            return TokenListPrice("DeepSeek Flash", "CNY", 2.0, 0.04, 8.0)
        }
        if (id.contains("deepseek-v4-pro") || id == "deepseek-reasoner") {
            return TokenListPrice("DeepSeek V4 Pro", "CNY", 9.0, 0.30, 27.0)
        }
        if (id.startsWith("qwen3.7-flash")) {
            val intl = baseUrl.contains("intl", ignoreCase = true)
            return if (intl) {
                TokenListPrice("Qwen3.7 Flash", "USD", 0.030, 0.006, 0.130)
            } else {
                TokenListPrice("Qwen3.7 Flash", "CNY", 0.20, 0.04, 0.80)
            }
        }
        return null
    }

    private const val REFERENCE_NOTE =
        "这是参考值，不是账单。你可能换过模型；这里全部按当前模型的高峰时段单价估算，" +
            "没有按每次请求的时段分开。官网价格会调整。"
}
