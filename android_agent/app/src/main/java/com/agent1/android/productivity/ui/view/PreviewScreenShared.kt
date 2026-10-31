package com.agent1.android.productivity.ui.view

import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agent1.android.productivity.logic.business.PreviewWorkspaceResources
import java.io.ByteArrayInputStream
import java.nio.file.Path

@Composable
internal fun PreviewErrorBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 虚拟源上的工作区文件；其它地址交给 WebView 自己加载。 */
internal fun interceptPreviewWorkspace(workspaceRoot: Path?, url: String?): WebResourceResponse? {
    val root = workspaceRoot ?: return null
    val raw = url ?: return null
    return when (val hit = PreviewWorkspaceResources.lookup(root, raw)) {
        PreviewWorkspaceResources.Lookup.NotOurs -> null
        PreviewWorkspaceResources.Lookup.Missing -> WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
        is PreviewWorkspaceResources.Lookup.Found -> WebResourceResponse(
            hit.mimeType,
            hit.encoding,
            ByteArrayInputStream(hit.bytes),
        )
    }
}

internal fun <T : ViewModel> previewViewModelFactory(create: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
}
