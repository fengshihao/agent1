package com.agent1.android.productivity.logic.data

import com.agent1.javaagent.todo.TodoStore
import java.nio.file.Path

internal data class SessionTodoRecord(
    val id: String,
    val content: String,
    val status: String,
)

/** 读取 {@code sessions/<id>/todos.json}（与 core {@link TodoStore} 同格式）。 */
object SessionTodoStore {

    fun load(sessionDir: Path): List<SessionTodoRecord> {
        val loaded = TodoStore(sessionDir).load()
        if (!loaded.ok()) return emptyList()
        return loaded.list().items().map { item ->
            SessionTodoRecord(
                id = item.id(),
                content = item.content(),
                status = item.status(),
            )
        }
    }
}
