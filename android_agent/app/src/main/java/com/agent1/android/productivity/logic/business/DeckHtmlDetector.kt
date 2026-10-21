package com.agent1.android.productivity.logic.business

import com.agent1.javaagent.html.inspect.DeckHtmlDetector as CoreDeckHtmlDetector
import java.io.File

/** @see CoreDeckHtmlDetector */
object DeckHtmlDetector {

    const val MAX_SCAN_BYTES: Int = CoreDeckHtmlDetector.MAX_SCAN_BYTES

    fun isDeckHtml(html: String): Boolean = CoreDeckHtmlDetector.isDeckHtml(html)

    fun scanFile(file: File?): Boolean {
        if (file == null) return false
        return CoreDeckHtmlDetector.scanFile(file.toPath())
    }
}
