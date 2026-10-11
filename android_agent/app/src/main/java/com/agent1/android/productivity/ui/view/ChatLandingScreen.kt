package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agent1.android.R
import androidx.compose.ui.unit.dp

/** 无活动会话时的落位页：顶栏 + 禁用态输入条 + 启动错误提示。 */
@Composable
internal fun ChatLanding(
    startupError: String?,
    exportInProgress: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onExportDiagnostics: () -> Unit,
    /** 打开产物库（顶栏文件夹，位于「更多」左侧；无活动会话时「插入会话」禁用）。 */
    onOpenArtifacts: () -> Unit = {},
    /** 输入框右下角的当前模型名；空则不显示。 */
    modelLabel: String = "",
    onOpenAbout: () -> Unit = {},
) {
    var moreMenu by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AgentTopBar(
                    title = stringResource(R.string.chat_new_session),
                    subtitle = null,
                    leading = {
                        TopBarIconButton(
                            icon = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_session_list),
                            onClick = onOpenDrawer,
                        )
                    },
                    actions = {
                        TopBarIconButton(
                            icon = AgentIcons.Folder,
                            contentDescription = stringResource(R.string.cd_artifact_library),
                            onClick = onOpenArtifacts,
                        )
                        ChatMoreMenu(
                            expanded = moreMenu,
                            exportInProgress = exportInProgress,
                            onOpen = { moreMenu = true },
                            onDismiss = { moreMenu = false },
                            onOpenSettings = onOpenSettings,
                            onOpenMcp = onOpenMcp,
                            onOpenCapabilities = onOpenCapabilities,
                            onExportDiagnostics = onExportDiagnostics,
                            onOpenAbout = onOpenAbout,
                        )
                    },
                )
            }
        },
        bottomBar = {
            ChatComposer(
                value = "",
                onValueChange = {},
                isRunning = false,
                canSend = false,
                enabled = false,
                inputPlaceholder = stringResource(R.string.chat_input_hint),
                onPickFiles = {},
                pickFilesEnabled = false,
                modelLabel = modelLabel,
                onOpenModel = onOpenSettings,
                onSend = {},
                onStop = {},
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (startupError.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.chat_opening),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.chat_startup_error, startupError),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            AgentTopFade(Modifier.align(Alignment.TopCenter))
        }
    }
}
