package com.agent1.android.productivity.logic.business

/**
 * 从选中元素生成稳健 CSS 选择器（18 号规划 Phase B）。
 * 优先级：#id → 最近带 id 祖先 + tag → tag.firstClass → tag:nth-of-type(n) 祖先链。
 * 无 document 唯一性验证——selector 供 AI 源码定位与 UI 展示，精确 DOM 查询非目标。
 */
object ElementSelectorBuilder {

    /** 兜底路径最多保留的祖先层数，避免超长 nth 链。 */
    private const val MAX_PATH_ANCESTORS = 3

    fun build(element: InspectedElement): String {
        val targetId = element.id.trim()
        if (targetId.isNotEmpty()) return "#${cssEscape(targetId)}"

        val ancestorWithId = element.ancestors.firstOrNull { it.id.trim().isNotEmpty() }
        if (ancestorWithId != null) {
            return "#${cssEscape(ancestorWithId.id.trim())} ${element.tag}"
        }

        val firstClass = element.classes.firstOrNull()
        if (firstClass != null) {
            return "${element.tag}.${cssEscape(firstClass)}"
        }

        // 兜底：tag:nth-of-type(n) 链，近 → 远取最多 MAX_PATH_ANCESTORS 层
        val ancestors = element.ancestors.take(MAX_PATH_ANCESTORS)
        if (ancestors.isEmpty()) return element.tag
        val path = buildString {
            ancestors.reversed().forEach { anc ->
                append(anc.tag)
                append(":nth-of-type(")
                append(anc.nth)
                append(")")
                append(" ")
            }
            append(element.tag)
        }
        return path
    }

    /** CSS 标识符转义（id/class 常规字母数字外字符），保守处理避免伪选择符注入。 */
    private fun cssEscape(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return cleaned.ifEmpty { raw }
    }
}