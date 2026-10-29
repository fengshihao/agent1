package com.agent1.android.productivity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
import com.agent1.javaagent.catalog.HostWebRegistrySync
import java.io.File

data class DocxPreviewState(
    val file: File? = null,
    val error: String? = null,
    val shellUrl: String = HostWebRegistrySync.defaultShellUrl(),
)

class DocxPreviewViewModel(
    appContext: Context,
    sessionId: String,
    relativePath: String,
) : ViewModel() {

    val state: DocxPreviewState = resolve(
        workspaceRoot = SessionWorkspacePaths.workspaceRoot(appContext, sessionId),
        agentRoot = SessionWorkspacePaths.agentRoot(appContext),
        sessionId = sessionId,
        relativePath = relativePath,
    )

    companion object {
        fun resolve(
            workspaceRoot: java.nio.file.Path?,
            agentRoot: java.nio.file.Path,
            sessionId: String,
            relativePath: String,
        ): DocxPreviewState {
            val urls = HostWebRegistrySync.docxPreviewUrls(agentRoot)
            if (sessionId.isBlank() || workspaceRoot == null) {
                return DocxPreviewState(error = "会话不存在，无法预览", shellUrl = urls.shell())
            }
            if (!WorkspaceFileActions.isDocxPreviewableInApp(relativePath)) {
                return DocxPreviewState(
                    error = "不支持的预览文件类型：$relativePath",
                    shellUrl = urls.shell(),
                )
            }
            val file = SessionWorkspacePaths.resolveFile(workspaceRoot, relativePath)
                ?: return DocxPreviewState(
                    error = "文件不存在或已被移动：$relativePath",
                    shellUrl = urls.shell(),
                )
            return DocxPreviewState(file = file, shellUrl = urls.shell())
        }
    }
}
