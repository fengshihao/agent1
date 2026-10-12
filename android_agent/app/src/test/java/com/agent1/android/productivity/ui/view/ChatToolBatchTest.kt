package com.agent1.android.productivity.ui.view

import com.agent1.android.productivity.ui.viewmodel.ChatLine
import com.agent1.android.productivity.ui.viewmodel.ChatRunTimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatToolBatchTest {

    @Test
    fun piecesFromLines_foldsReasoningAndToolsOfOneTurn() {
        val lines = listOf(
            ChatLine(role = "assistant", content = "先看一下", reasoning = "想一下", stableKey = "a1"),
            ChatLine(role = "tool", content = "ok", isTool = true, toolName = "read_file", stableKey = "t1"),
            ChatLine(role = "tool", content = "fail", isTool = true, toolName = "run_js", toolIsError = true, stableKey = "t2"),
            ChatLine(role = "assistant", content = "好了", reasoning = "再想", stableKey = "a2"),
        )
        val pieces = ChatToolBatch.piecesFromLines(lines)
        assertEquals(4, pieces.size)
        assertTrue(pieces[0] is TurnPiece.Reasoning)
        assertTrue(pieces[1] is TurnPiece.Tool)
        assertTrue(pieces[3] is TurnPiece.Reasoning)
        assertEquals(
            "思考并调用了 read_file、run_js · 共 2 次 · 1 次失败",
            ChatToolBatch.title(pieces),
        )
    }

    @Test
    fun groupTimeline_putsWholeTurnIntoOneCardBeforeAnswers() {
        val items = listOf(
            ChatRunTimelineItem.AssistantPart(id = "a1", content = "先看一下", reasoning = "想一下"),
            ChatRunTimelineItem.ToolPart(id = "c1", toolCallId = "1", toolName = "read_file", finished = true, resultSummary = "package"),
            ChatRunTimelineItem.ToolPart(id = "c2", toolCallId = "2", toolName = "grep", finished = false),
            ChatRunTimelineItem.AssistantPart(id = "a2", content = "结论"),
        )
        val rows = ChatToolBatch.groupTimeline(items, streamingReasoning = "还在想")
        assertEquals(3, rows.size)
        val activity = rows[0] as RunTimelineRow.Activity
        assertEquals("live-turn-activity", activity.key)
        assertEquals(4, activity.pieces.size)
        assertTrue(activity.pieces.last() is TurnPiece.Reasoning)
        assertEquals("a1", (rows[1] as RunTimelineRow.Assistant).part.id)
        assertEquals("", (rows[1] as RunTimelineRow.Assistant).part.reasoning)
    }

    @Test
    fun groupTimeline_keepsActivityCardWhileRunIsEmpty() {
        assertEquals(0, ChatToolBatch.groupTimeline(emptyList(), isRunning = false).size)
        val rows = ChatToolBatch.groupTimeline(emptyList(), isRunning = true)
        assertEquals(1, rows.size)
        val activity = rows[0] as RunTimelineRow.Activity
        assertEquals("live-turn-activity", activity.key)
        assertTrue(activity.pieces.isEmpty())
        assertEquals("思考与执行", ChatToolBatch.title(activity.pieces))
    }

    @Test
    fun title_namesCallsAndFailures() {
        val pieces = listOf(
            TurnPiece.Reasoning("r", "先看结构", false),
            TurnPiece.Tool(ToolBatchEntry("1", "read_file", "", true, false, "ok", emptyList())),
            TurnPiece.Tool(ToolBatchEntry("2", "run_js", "", true, true, "boom", emptyList())),
            TurnPiece.Tool(ToolBatchEntry("3", "grep", "", false, false, "", listOf("scanning"))),
        )
        assertEquals(
            "正在调用 grep · 共 3 次 · 进行中 1 · 1 次失败",
            ChatToolBatch.title(pieces),
        )
        assertEquals("scanning", ChatToolBatch.detail(pieces))
        assertEquals("grep · scanning", ChatToolBatch.pieceLabel(pieces.last()))
    }

    @Test
    fun failureMarkComesFromTheToolFlagNotTheWording() {
        val flagged = ToolBatchEntry(
            key = "u1",
            toolName = "read_url",
            argsPreview = "",
            finished = true,
            isError = true,
            resultSummary = "未能抽出正文",
            progressLines = emptyList(),
        )
        val wordingOnly = flagged.copy(key = "u2", isError = false, resultSummary = "错误：未能抽出正文")
        assertTrue(ChatToolBatch.showsFailure(flagged))
        assertEquals(false, ChatToolBatch.showsFailure(wordingOnly))
        assertEquals(
            "read_url 失败 · 未能抽出正文",
            ChatToolBatch.pieceLabel(TurnPiece.Tool(flagged)),
        )
    }
}
