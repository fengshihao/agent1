package com.agent1.android.productivity.logic.business

import org.json.JSONArray
import org.json.JSONObject

/** 审查模式选中的元素（tap 回传结构，见 [HtmlInspectScript]）。 */
data class InspectedElement(
    val tag: String,
    val id: String = "",
    val classes: List<String> = emptyList(),
    val textPreview: String = "",
    /** 截断后的 outerHTML（JS 侧 400 字上限）。 */
    val outerHtml: String = "",
    /** 祖先链，近 → 远；每层含 nth-of-type 序号。 */
    val ancestors: List<InspectAncestor> = emptyList(),
)

data class InspectAncestor(
    val tag: String,
    val id: String = "",
    val classes: List<String> = emptyList(),
    val nth: Int = 1,
)

/** 桥回传 JSON → [InspectedElement]，容错：字段缺失/类型不符取默认值，整体损坏返回 null。 */
object HtmlInspectParser {

    fun parse(json: String): InspectedElement? {
        if (json.isBlank()) return null
        return try {
            parseObject(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseObject(json: JSONObject): InspectedElement? {
        val tag = json.optString("tag").trim().lowercase()
        if (tag.isEmpty()) return null
        return InspectedElement(
            tag = tag,
            id = json.optString("id").trim(),
            classes = parseStringList(json.optJSONArray("classes")),
            textPreview = json.optString("textPreview").trim(),
            outerHtml = json.optString("outerHtml").trim(),
            ancestors = parseAncestors(json.optJSONArray("ancestors")),
        )
    }

    private fun parseAncestors(array: JSONArray?): List<InspectAncestor> {
        if (array == null) return emptyList()
        val out = mutableListOf<InspectAncestor>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val tag = obj.optString("tag").trim().lowercase()
            if (tag.isEmpty()) continue
            val nth = obj.optInt("nth", 1).takeIf { it >= 1 } ?: 1
            out.add(
                InspectAncestor(
                    tag = tag,
                    id = obj.optString("id").trim(),
                    classes = parseStringList(obj.optJSONArray("classes")),
                    nth = nth,
                ),
            )
        }
        return out
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}