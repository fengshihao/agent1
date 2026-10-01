package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.rememberAsyncImagePainter
import com.agent1.android.productivity.logic.business.SessionWorkspacePaths
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
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
    val context = LocalContext.current
    val imageLoader = remember(context) { workspaceImageLoader(context) }
    val transformer = remember(root, imageLoader) {
        object : ImageTransformer {
            @Composable
            override fun transform(link: String): ImageData? {
                if (root.isBlank()) return null
                val file = SessionWorkspacePaths.resolveFile(Paths.get(root), link) ?: return null
                val painter = rememberAsyncImagePainter(model = file, imageLoader = imageLoader)
                return ImageData(painter = painter)
            }
        }
    }
    val body = MaterialTheme.typography.bodyLarge
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val label = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    Markdown(
        content = content,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        ),
        typography = markdownTypography(
            h1 = label,
            h2 = body.copy(fontWeight = FontWeight.SemiBold),
            h3 = bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            h4 = bodyMedium,
            h5 = bodyMedium,
            h6 = bodyMedium,
            text = body,
            paragraph = body,
            table = bodyMedium,
        ),
        imageTransformer = transformer,
    )
}
