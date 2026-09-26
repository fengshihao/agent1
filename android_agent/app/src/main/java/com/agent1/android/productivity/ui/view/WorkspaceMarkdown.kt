package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil.compose.rememberAsyncImagePainter
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import java.nio.file.Paths

@Composable
fun WorkspaceMarkdown(
    content: String,
    workspaceAbsolutePath: String,
    modifier: Modifier = Modifier,
) {
    val root = workspaceAbsolutePath
    val transformer = remember(root) {
        object : ImageTransformer {
            @Composable
            override fun transform(link: String): ImageData? {
                if (root.isBlank()) return null
                val file = SessionWorkspacePaths.resolveFile(Paths.get(root), link) ?: return null
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
