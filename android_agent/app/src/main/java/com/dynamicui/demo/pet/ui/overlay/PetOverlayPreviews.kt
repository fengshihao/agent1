package com.dynamicui.demo.pet.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 纯展示：聊天卡片样式（不依赖 Service / ASR）。 */
@Composable
fun PetOverlayChatPanelPreviewContent(
    modifier: Modifier = Modifier,
    bubbles: List<Pair<String, String>> = listOf("user" to "你好", "assistant" to "预览回复")
) {
    Card(
        modifier = modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E))
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                text = "对话（预览）",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            bubbles.forEach { (role, content) ->
                val bg = if (role == "user") Color(0xFF424242) else Color(0xFF2D2D2D)
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(bg, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF303030)
@Composable
private fun PetOverlayChatPanelPreview() {
    MaterialTheme {
        PetOverlayChatPanelPreviewContent()
    }
}
