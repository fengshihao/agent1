package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranscriptFormattingTest {

    @Test
    fun formatToolResult_parsesWebviewImageJson() {
        val raw = """
            {"ok":true,"elapsedMs":608,"outputPath":"cat.png","outputBytes":30534}
        """.trimIndent()
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals("cat.png", display.workspaceImagePath)
        assert(display.summary.contains("cat.png"))
        assert(display.summary.contains("30534"))
    }

    @Test
    fun trailingImagePreviewPaths_excludesInlineMarkdownImages() {
        val paths = ChatTranscriptFormatting.trailingImagePreviewPaths(
            "看 ![内联](a.png) 与附件",
            listOf("a.png", "b.png"),
            null,
        )
        assertEquals(listOf("b.png"), paths)
    }

    @Test
    fun extractMarkdownImagePaths_findsRelativePath() {
        val paths = ChatTranscriptFormatting.extractMarkdownImagePaths("看图 ![小猫](cat.png) 结束")
        assertEquals(listOf("cat.png"), paths)
    }

    @Test
    fun formatToolResult_parsesDocxOutputPath() {
        val raw = """{"ok":true,"path":"out/report.docx","bytes":12000}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals(listOf("out/report.docx"), display.workspaceFilePaths)
        assert(display.summary.contains("report.docx"))
    }

    @Test
    fun formatToolResult_plainWriteExtractsPath() {
        val display = ChatTranscriptFormatting.formatToolResult("已写入: out/report.docx (120 chars)", null)
        assertEquals(listOf("out/report.docx"), display.workspaceFilePaths)
    }

    @Test
    fun extractPlainWorkspacePaths_findsBareDocx() {
        val paths = ChatTranscriptFormatting.extractPlainWorkspacePaths("见 out/a.docx 与 notes.md")
        assertEquals(listOf("out/a.docx", "notes.md"), paths)
    }

    @Test
    fun extractPlainWorkspacePaths_findsChineseFileName() {
        val paths = ChatTranscriptFormatting.extractPlainWorkspacePaths(
            "成果：大模型7天学习大纲.docx 和 大模型7天学习大纲.md",
        )
        assertEquals(listOf("大模型7天学习大纲.docx", "大模型7天学习大纲.md"), paths)
    }

    @Test
    fun mergeWorkspaceFilePaths_combinesSources() {
        val merged = ChatTranscriptFormatting.mergeWorkspaceFilePaths(
            "下载 [报告](out/a.docx) 或 out/b.pdf",
            listOf("imports/c.csv"),
        )
        assertEquals(listOf("imports/c.csv", "out/a.docx", "out/b.pdf"), merged)
    }

    @Test
    fun mergeWorkspaceFilePaths_includesMarkdownImages() {
        val merged = ChatTranscriptFormatting.mergeWorkspaceFilePaths(
            "看 ![图](cat.png)",
            emptyList(),
        )
        assertEquals(listOf("cat.png"), merged)
    }

    @Test
    fun linkifyBareWorkspacePaths_wrapsUnlinkedPaths() {
        val out = ChatTranscriptFormatting.linkifyBareWorkspacePaths(
            "文件在 out/x.docx",
            listOf("out/x.docx"),
        )
        assertEquals("文件在 [x.docx](out/x.docx)", out)
    }

    @Test
    fun linkifyBareWorkspacePaths_keepsExistingMarkdownLinkIntact() {
        // 真机回归：短路径命中已有链接目标里的尾部子串，把 href 撕成
        // out[特斯拉电动汽车简史.pptx](特斯拉电动汽车简史.pptx) 导致点击打不开
        val content = "成品：**[特斯拉电动汽车简史.pptx](out/特斯拉电动汽车简史.pptx)**（约 14.7 MB）"
        val out = ChatTranscriptFormatting.linkifyBareWorkspacePaths(
            content,
            listOf("特斯拉电动汽车简史.pptx", "out/特斯拉电动汽车简史.pptx"),
        )
        assertEquals(content, out)
    }

    @Test
    fun linkifyBareWorkspacePaths_doesNotSplitLongerPathTail() {
        // x.docx 只是 out/x.docx 的尾部；文件不在根下，撕开只会生成坏链接
        val out = ChatTranscriptFormatting.linkifyBareWorkspacePaths(
            "见 out/x.docx",
            listOf("x.docx"),
        )
        assertEquals("见 out/x.docx", out)
    }

    @Test
    fun extractPlainWorkspacePaths_ignoresMarkdownLinkLabel() {
        // Markdown 链接 label 是显示文字，不是文件路径
        val paths = ChatTranscriptFormatting.extractPlainWorkspacePaths("见 [报告.docx](out/报告.docx)")
        assertEquals(emptyList<String>(), paths)
    }

    @Test
    fun rewriteWorkspaceMarkdownHrefs_afterLinkify_keepsHref() {
        // 端到端回归：渲染链路 linkify → rewrite 后 href 必须仍指向真实文件
        val content = "成品：**[特斯拉电动汽车简史.pptx](out/特斯拉电动汽车简史.pptx)**"
        val paths = ChatTranscriptFormatting.mergeWorkspaceFilePaths(content, emptyList())
        val out = ChatTranscriptFormatting.rewriteWorkspaceMarkdownHrefs(
            ChatTranscriptFormatting.linkifyBareWorkspacePaths(content, paths),
        )
        assertEquals(
            "成品：**[特斯拉电动汽车简史.pptx](agent1-file:out/特斯拉电动汽车简史.pptx)**",
            out,
        )
    }

    @Test
    fun splitLinkifiedSegments_doesNotSplitPathTail() {
        val segments = ChatTranscriptFormatting.splitLinkifiedSegments(
            "已生成 out/a.docx",
            listOf("a.docx"),
        )
        assertEquals(1, segments.size)
        assertNull(segments[0].workspacePath)
        assertEquals("已生成 out/a.docx", segments[0].text)
    }

    @Test
    fun splitLinkifiedSegments_splitsAroundPath() {
        val segments = ChatTranscriptFormatting.splitLinkifiedSegments(
            "已生成 out/a.docx 完成",
            listOf("out/a.docx"),
        )
        assertEquals(3, segments.size)
        assertEquals("已生成 ", segments[0].text)
        assertEquals("out/a.docx", segments[1].workspacePath)
        assertEquals(" 完成", segments[2].text)
    }

    @Test
    fun splitLinkifiedSegments_parsesMarkdownFileLinkAsHyperlink() {
        val segments = ChatTranscriptFormatting.splitLinkifiedSegments(
            "完成 [xxx.png](xxx.png)",
            emptyList(),
        )
        assertEquals(2, segments.size)
        assertEquals("完成 ", segments[0].text)
        assertEquals("xxx.png", segments[1].workspacePath)
        assertEquals("xxx.png", segments[1].text)
    }

    @Test
    fun splitLinkifiedSegments_parsesImageMarkdownAsFileLink() {
        val segments = ChatTranscriptFormatting.splitLinkifiedSegments(
            "看图 ![小猫](cat.png)",
            emptyList(),
        )
        assertEquals(2, segments.size)
        assertEquals("看图 ", segments[0].text)
        assertEquals("cat.png", segments[1].workspacePath)
        assertEquals("小猫", segments[1].text)
    }

    @Test
    fun rewriteWorkspaceMarkdownHrefs_addsScheme() {
        val out = ChatTranscriptFormatting.rewriteWorkspaceMarkdownHrefs("见 [报告](out/a.docx)")
        assertEquals("见 [报告](agent1-file:out/a.docx)", out)
    }

    @Test
    fun splitLinkifiedSegments_skipsBareImagePathWhenMarkdownImagePresent() {
        val content = "已生成 cat.png\n\n![预览](cat.png)"
        val segments = ChatTranscriptFormatting.splitLinkifiedSegments(content, listOf("cat.png"))
        val imageSegments = segments.filter { it.workspacePath == "cat.png" }
        assertEquals(1, imageSegments.size)
        assertTrue(segments.any { it.text.contains("已生成 cat.png") })
    }

    @Test
    fun rewriteWorkspaceMarkdownHrefs_leavesImages() {
        val src = "图 ![小猫](cat.png)"
        assertEquals(src, ChatTranscriptFormatting.rewriteWorkspaceMarkdownHrefs(src))
    }

    @Test
    fun mergeWorkspaceFilePaths_stripsWorkspacePrefixInMarkdown() {
        val merged = ChatTranscriptFormatting.mergeWorkspaceFilePaths(
            "见 [报告](workspace/out/a.docx)",
            emptyList(),
        )
        assertEquals(listOf("out/a.docx"), merged)
    }

    @Test
    fun extractMarkdownFileLinks_findsDocxLink() {
        val paths = ChatTranscriptFormatting.extractMarkdownFileLinks("见 [报告](out/a.docx) 和 ![图](x.png)")
        assertEquals(listOf("out/a.docx"), paths)
    }

    @Test
    fun extractMarkdownFileLinks_skipsHttp() {
        val paths = ChatTranscriptFormatting.extractMarkdownFileLinks("[外](https://example.com/x.docx)")
        assertEquals(emptyList<String>(), paths)
    }

    @Test
    fun formatToolResult_nullSpillErrorIsNotAnImage() {
        val raw = """{"ok":false,"error":"没有可落盘的返回值:脚本返回了 null 或 undefined。"}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertNull(display.workspaceImagePath)
        assertTrue(display.summary.contains("没有可落盘的返回值"))
        assertFalse(display.summary.contains("已生成图片"))
    }

    @Test
    fun formatToolResult_includesResultType() {
        val raw = """{"ok":true,"outputPath":"cat.png","outputBytes":10,"resultType":"string","elapsedMs":12}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals("cat.png", display.workspaceImagePath)
        assertTrue(display.summary.contains("string"))
        assertTrue(display.summary.contains("已生成图片"))
    }

    @Test
    fun formatToolResult_webviewAutoSpillB64IsImagePreview() {
        val raw = """
            {"ok":true,"outputPath":"tmp/webview_exec/wv-1-1.b64","outputBytes":100,"resultType":"string","elapsedMs":12}
        """.trimIndent()
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals("tmp/webview_exec/wv-1-1.b64", display.workspaceImagePath)
        assertTrue(display.summary.contains("已生成图片"))
    }

    @Test
    fun workspaceImageBytes_decodesBase64PngAndRejectsNullText() {
        val dir = Files.createTempDirectory("webview-img")
        val base64 = dir.resolve("puppy.png").toFile()
        base64.writeText(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        assertTrue(WorkspaceImageBytes.isBase64ImageText(base64))
        val decoded = checkNotNull(WorkspaceImageBytes.decodeBase64Payload(base64))
        assertTrue(decoded.size > 8)
        assertEquals(0x89.toByte(), decoded[0])
        assertEquals('P'.code.toByte(), decoded[1])

        val nullText = dir.resolve("empty.png").toFile()
        nullText.writeText("null")
        assertFalse(WorkspaceImageBytes.isBase64ImageText(nullText))
        assertNull(WorkspaceImageBytes.decodeBase64Payload(nullText))
    }

    @Test
    fun formatToolResult_plainText() {
        val display = ChatTranscriptFormatting.formatToolResult("hello tool", null)
        assertEquals("hello tool", display.summary)
        assertNull(display.workspaceImagePath)
    }

    @Test
    fun formatToolResult_parsesHtmlOutputPath() {
        val raw = """{"ok":true,"path":"out/index.html","bytes":512}"""
        val display = ChatTranscriptFormatting.formatToolResult(raw, null)
        assertEquals(listOf("out/index.html"), display.workspaceFilePaths)
        assert(display.summary.contains("index.html"))
    }

    @Test
    fun extractMarkdownFileLinks_findsHtmlLink() {
        val paths = ChatTranscriptFormatting.extractMarkdownFileLinks("预览 [页面](out/index.html)")
        assertEquals(listOf("out/index.html"), paths)
    }

    @Test
    fun extractPlainWorkspacePaths_findsBareHtml() {
        val paths = ChatTranscriptFormatting.extractPlainWorkspacePaths("见 out/index.html 与 demo.htm")
        assertEquals(listOf("out/index.html", "demo.htm"), paths)
    }

    @Test
    fun splitLinkifiedSegments_htmlLinkIsWorkspacePath() {
        val segments = ChatTranscriptFormatting.splitLinkifiedSegments(
            "已生成 [预览页](out/index.html)",
            emptyList(),
        )
        assertEquals("out/index.html", segments.last().workspacePath)
    }
}
