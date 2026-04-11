package com.dynamicui.demo.pet.logic.data.accessibility.tools

import android.content.ContentUris
import android.content.Context
import android.provider.BaseColumns
import android.provider.MediaStore
import com.agent1.javaagent.core.CancellationToken
import com.agent1.javaagent.tool.AgentTool
import com.agent1.javaagent.tool.ToolExecutionResult
import com.agent1.javaagent.tool.ToolUpdateListener
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class QueryMediaStoreTool(
    private val context: Context
) : AgentTool {
    override fun name(): String = "query_media_store"

    override fun description(): String = "查询媒体库文件（图片/视频/音频/通用文件），支持按时间和关键字过滤。"

    override fun parametersSchema(): JsonNode {
        val schema = MAPPER.createObjectNode()
        schema.put("type", "object")
        val p = schema.putObject("properties")
        p.putObject("kind").put("type", "string").put("description", "images/videos/audio/files，默认 files。")
        p.putObject("name_query").put("type", "string").put("description", "按文件名模糊过滤。")
        p.putObject("mime_prefix").put("type", "string").put("description", "MIME 前缀，如 image/ 或 application/vnd.openxmlformats。")
        p.putObject("start_ms").put("type", "integer").put("description", "开始时间(毫秒)，匹配 DATE_ADDED。")
        p.putObject("end_ms").put("type", "integer").put("description", "结束时间(毫秒)，匹配 DATE_ADDED。")
        p.putObject("limit").put("type", "integer").put("description", "最多返回条数，默认 50，最大 200。")
        schema.putArray("required")
        return schema
    }

    override fun execute(
        toolCallId: String,
        parameters: JsonNode,
        cancellationToken: CancellationToken,
        onUpdate: ToolUpdateListener
    ): ToolExecutionResult {
        if (cancellationToken.isCancelled) {
            return ToolExecutionResult.text("""{"ok":false,"reason":"执行已取消"}""")
        }
        val kind = parameters.path("kind").asText("files")
        val nameQuery = parameters.path("name_query").asText("").trim()
        val mimePrefix = parameters.path("mime_prefix").asText("").trim()
        val startMs = parameters.path("start_ms").asLong(0L)
        val endMs = parameters.path("end_ms").asLong(0L)
        val limit = parameters.path("limit").asInt(50).coerceIn(1, 200)

        val uri = when (kind.lowercase()) {
            "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (nameQuery.isNotEmpty()) {
            where += "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            args += "%$nameQuery%"
        }
        if (mimePrefix.isNotEmpty()) {
            where += "${MediaStore.MediaColumns.MIME_TYPE} LIKE ?"
            args += "$mimePrefix%"
        }
        if (startMs > 0L) {
            where += "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
            args += (startMs / 1000L).toString()
        }
        if (endMs > 0L) {
            where += "${MediaStore.MediaColumns.DATE_ADDED} <= ?"
            args += (endMs / 1000L).toString()
        }
        val selection = if (where.isEmpty()) null else where.joinToString(" AND ")

        val out = MAPPER.createObjectNode()
        val items = MAPPER.createArrayNode()
        var count = 0
        context.contentResolver.query(
            uri,
            projection,
            selection,
            if (args.isEmpty()) null else args.toTypedArray(),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(BaseColumns._ID)
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val addedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext() && count < limit) {
                val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                val item = MAPPER.createObjectNode()
                item.put("id", id)
                item.put("displayName", if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else "")
                item.put("mimeType", if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else "")
                item.put("size", if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L)
                item.put("dateAddedSec", if (addedIdx >= 0) cursor.getLong(addedIdx) else 0L)
                item.put("relativePath", if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "")
                item.put("uri", if (id >= 0) ContentUris.withAppendedId(uri, id).toString() else uri.toString())
                items.add(item)
                count += 1
            }
        }
        out.put("ok", true)
        out.put("kind", kind)
        out.put("count", count)
        out.putArray("items").addAll(items)
        return ToolExecutionResult.text(MAPPER.writeValueAsString(out))
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
