package com.agent1.android.productivity.logic.business

import java.io.File
import org.json.JSONObject

/** 墨览预览的文件读取与桥 JSON 组装（IO 归 logic 层，ui.view 不直接读盘）。 */
object MolanPreviewFileReader {

    /** 返回桥契约 JSON `{"name":"x.md","text":"…"}`；失败返回空串（壳侧提示）。 */
    fun readMdJson(absolutePath: String, displayName: String): String {
        return try {
            val text = File(absolutePath).readText(Charsets.UTF_8)
            if (text.isEmpty()) {
                ""
            } else {
                JSONObject().put("name", displayName).put("text", text).toString()
            }
        } catch (_: Exception) {
            ""
        }
    }
}
