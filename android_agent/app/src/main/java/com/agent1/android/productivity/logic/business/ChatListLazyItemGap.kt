package com.agent1.android.productivity.logic.business

/**
 * 聊天 LazyColumn 行顶间距：同一条消息的后续分段 [messageTightTop]==true 时贴紧上一段。
 * 须用非负 padding / 条件间距，禁止负 padding（Compose 会崩溃）。
 */
object ChatListLazyItemGap {
    /** @param messageTightTop 消息分段续行时为 true；非消息行传 null。 */
    fun shouldPadTop(index: Int, messageTightTop: Boolean?): Boolean =
        index > 0 && messageTightTop != true
}
