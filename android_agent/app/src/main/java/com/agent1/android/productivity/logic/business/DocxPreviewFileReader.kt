package com.agent1.android.productivity.logic.business

import java.io.File

object DocxPreviewFileReader {
    fun readBytes(absolutePath: String): ByteArray = File(absolutePath).readBytes()
}
