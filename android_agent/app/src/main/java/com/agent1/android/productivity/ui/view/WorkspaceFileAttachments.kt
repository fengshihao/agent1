package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.WorkspaceFileActions
import java.nio.file.Paths

/**
 * 正文引用的工作区文件列表：每个文件一张「文件名 + 打开 + 分享」卡片行。
 * 之前 markdown 模式下曾只渲染打开/分享图标（不带文件名），用户无法辨识，已移除该模式。
 */
@Composable
fun WorkspaceFileAttachments(
    workspaceAbsolutePath: String,
    relativePaths: List<String>,
    excludePaths: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    /** 传入且文件可内置预览（html）时，点「打开」走 app 内预览路由。 */
    onOpenInApp: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    if (workspaceAbsolutePath.isBlank()) return
    val paths = relativePaths.filter { it.isNotBlank() && it !in excludePaths }
    if (paths.isEmpty()) return
    val root = Paths.get(workspaceAbsolutePath)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        paths.forEach { relative ->
            WorkspaceFileLinkRow(
                relativePath = relative,
                onOpen = { WorkspaceFileActions.openWorkspaceFile(context, root, relative) },
                onShare = { WorkspaceFileActions.shareWorkspaceFile(context, root, relative) },
                onOpenInApp = onOpenInApp,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}