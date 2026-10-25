package com.agent1.android.productivity.logic.business

import java.nio.file.Path

/** docx 预览 P2：经 Weizhi readDocx 读块/写回；未集成 Weizhi 时 [backend] 为 null。 */
object DocxPreviewDocxOps {

    interface Backend {
        fun textViewJson(workspaceRoot: Path, relativePath: String): String?

        fun setBlockText(workspaceRoot: Path, relativePath: String, blockIndex: Int, text: String): String?

        fun setBlockStyle(
            workspaceRoot: Path,
            relativePath: String,
            blockIndex: Int,
            bold: Boolean?,
            sizePt: Int?,
        ): String?
    }

    @Volatile
    var backend: Backend? = null

    fun textViewJson(workspaceRoot: Path, relativePath: String): String? =
        backend?.textViewJson(workspaceRoot, relativePath)

    fun setBlockText(workspaceRoot: Path, relativePath: String, blockIndex: Int, text: String): String? =
        backend?.setBlockText(workspaceRoot, relativePath, blockIndex, text)

    fun setBlockStyle(
        workspaceRoot: Path,
        relativePath: String,
        blockIndex: Int,
        bold: Boolean?,
        sizePt: Int?,
    ): String? =
        backend?.setBlockStyle(workspaceRoot, relativePath, blockIndex, bold, sizePt)
}
