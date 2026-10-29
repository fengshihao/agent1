package com.agent1.android.productivity.logic.business

import com.agent1.android.productivity.logic.data.SessionTodoStore
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path

data class SessionTodoItem(
    val id: String,
    val content: String,
    val status: String,
)

/** 会话任务清单：磁盘读取与工具结果解析。 */
object SessionTodoAccess {

    fun loadFromSessionDir(sessionDir: Path): List<SessionTodoItem> =
        SessionTodoStore.load(sessionDir).map { record ->
            SessionTodoItem(
                id = record.id,
                content = record.content,
                status = record.status,
            )
        }

    fun parseTodoListFromToolDetails(details: JsonNode): List<SessionTodoItem>? {
        if (details.path("kind").asText() != "todo_list" || !details.has("todos")) return null
        val out = ArrayList<SessionTodoItem>()
        for (node in details.path("todos")) {
            val id = node.path("id").asText("").trim()
            val content = node.path("content").asText("").trim()
            if (id.isEmpty() || content.isEmpty()) continue
            out.add(
                SessionTodoItem(
                    id = id,
                    content = content,
                    status = node.path("status").asText("pending").trim(),
                ),
            )
        }
        return out
    }
}
