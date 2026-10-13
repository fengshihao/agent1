package com.agent1.android.productivity.logic.business

/** 用户发送消息时，把本轮附件增删说明拼进同一条 user 消息（不占额外轮次）。 */
object UserAttachmentMessageSuffix {

    data class ChangeSet(
        val addedPaths: List<String> = emptyList(),
        val removedPaths: List<String> = emptyList(),
    ) {
        fun isEmpty(): Boolean = addedPaths.isEmpty() && removedPaths.isEmpty()

        fun merge(other: ChangeSet): ChangeSet = ChangeSet(
            addedPaths = addedPaths + other.addedPaths,
            removedPaths = removedPaths + other.removedPaths,
        )
    }

    fun appendToUserMessage(body: String, changes: ChangeSet): String {
        val trimmed = body.trim()
        if (changes.isEmpty()) return trimmed
        val parts = buildList {
            if (changes.addedPaths.isNotEmpty()) {
                add("本次添加附件：${changes.addedPaths.joinToString("、")}")
            }
            if (changes.removedPaths.isNotEmpty()) {
                add("本次取消附件：${changes.removedPaths.joinToString("、")}")
            }
        }
        val suffix = parts.joinToString("；") + "。"
        return if (trimmed.isEmpty()) suffix else "$trimmed\n\n$suffix"
    }
}
