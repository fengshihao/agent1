package com.dynamicui.demo.llm

internal fun cleanModelJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed

    val withoutFence = trimmed
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    val start = withoutFence.indexOf('{')
    val end = withoutFence.lastIndexOf('}')
    if (start >= 0 && end > start) {
        return withoutFence.substring(start, end + 1).trim()
    }
    return withoutFence
}
