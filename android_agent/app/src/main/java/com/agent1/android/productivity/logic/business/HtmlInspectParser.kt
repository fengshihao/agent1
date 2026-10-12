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
    /** 渲染快照（computed style + 尺寸）；取不到时为 null。 */
    val render: InspectRenderInfo? = null,
    /** 祖先链，近 → 远；每层含 nth-of-type 序号。 */
    val ancestors: List<InspectAncestor> = emptyList(),
)

/** 选中元素的渲染快照，帮助 AI 不跑 webview 诊断即可定位颜色/字号/尺寸类问题。 */
data class InspectRenderInfo(
    val color: String = "",
    val backgroundColor: String = "",
    val fontSize: String = "",
    val display: String = "",
    val width: Int? = null,
    val height: Int? = null,
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
            render = parseRenderInfo(json.optJSONObject("render")),
            ancestors = parseAncestors(json.optJSONArray("ancestors")),
        )
    }

    private fun parseRenderInfo(json: JSONObject?): InspectRenderInfo? {
        if (json == null) return null
        // Android org.json 的 optString 会把任意类型强转成字符串（对象→JSON 串），
        // 这里用严格字符串判断，损坏字段一律回落默认值。
        return InspectRenderInfo(
            color = json.opt("color") as? String ?: "",
            backgroundColor = json.opt("backgroundColor") as? String ?: "",
            fontSize = json.opt("fontSize") as? String ?: "",
            display = json.opt("display") as? String ?: "",
            width = json.optInt("width", -1).takeIf { it >= 0 },
            height = json.optInt("height", -1).takeIf { it >= 0 },
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