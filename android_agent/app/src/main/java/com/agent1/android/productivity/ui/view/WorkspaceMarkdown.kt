package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import com.agent1.android.productivity.logic.data.SessionWorkspace
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

@Composable
fun WorkspaceMarkdown(
    content: String,
    sessionId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val transformer = remember(sessionId) {
        object : ImageTransformer {
            @Composable
            override fun transform(link: String): ImageData? {
                val file = SessionWorkspace.resolveFile(context, sessionId, link) ?: return null
                val painter = rememberAsyncImagePainter(model = file)
                return ImageData(painter = painter)
            }
        }
    }
    Markdown(
        content = content,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        ),
        imageTransformer = transformer,
    )
}
