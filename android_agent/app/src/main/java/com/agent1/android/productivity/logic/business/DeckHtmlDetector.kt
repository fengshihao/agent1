package com.agent1.android.productivity.logic.business

import java.io.File
import java.io.InputStream

/**
 * deck.html 检测（21 号规划 T1：预览页自动进入 deck 模式）。
 *
 * html2pptx 约定每页一个 `<section class="slide">`（[SKILL.md]），
 * 预览检测同口径：HTML 里有 class 属性含独立单词 `slide` 的元素即视为 deck。
 * 命中只影响预览形态（缩放适配/全屏），误报（如轮播图同名 class）无破坏性。
 *
 * 纯字符串/文件头扫描，不触 Android UI；deck 源不带内联大图（图片走
 * workspace 相对路径，导出时才内联），头部扫描足够覆盖第一个 slide 标签。
 */
object DeckHtmlDetector {

    /**
     * class 属性值含独立单词 slide（兼容单双引号、多 class）。
     * 显式双分支而非反向引用：Java 字符类内 `\1` 不按 backreference 解析。
     */
    private val SLIDE_CLASS_ATTR = Regex(
        """class\s*=\s*"[^"]*\bslide\b[^"]*"|class\s*=\s*'[^']*\bslide\b[^']*'""",
    )

    /** 只扫文件头：deck.html 首个 `<section class="slide">` 远在 256KB 内。 */
    const val MAX_SCAN_BYTES: Int = 256 * 1024

    /** 纯函数：HTML 文本是否为 deck。 */
    fun isDeckHtml(html: String): Boolean = SLIDE_CLASS_ATTR.containsMatchIn(html)

    /** 读文件头 [MAX_SCAN_BYTES] 字节检测；文件缺失/读取失败按非 deck 处理。 */
    fun scanFile(file: File?): Boolean {
        if (file == null || !file.isFile) return false
        val bytes = file.inputStream().use { stream: InputStream -> readHead(stream) } ?: return false
        return isDeckHtml(String(bytes, Charsets.UTF_8))
    }

    private fun readHead(stream: InputStream): ByteArray? = runCatching {
        val buf = ByteArray(MAX_SCAN_BYTES)
        var off = 0
        while (off < buf.size) {
            val n = stream.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        buf.copyOf(off)
    }.getOrNull()
}
