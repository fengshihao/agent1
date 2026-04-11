package com.dynamicui.demo.pet.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dynamicui.demo.R
import com.dynamicui.demo.pet.ui.viewmodel.PetEntryUiState

@Composable
fun PetEntryContent(
    modifier: Modifier = Modifier,
    uiState: PetEntryUiState,
    onRequestMic: () -> Unit,
    onRequestCalendar: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onShowOverlay: () -> Unit,
    onHideOverlay: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("悬浮宠物", style = MaterialTheme.typography.titleLarge)
        Text(
            "先启动前台服务，再授予悬浮窗与麦克风；长按右下角宠物说话。",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onRequestMic, modifier = Modifier.fillMaxWidth()) {
            Text("请求麦克风权限")
        }
        Button(onClick = onRequestCalendar, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pet_calendar_permission))
        }
        Button(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
            Text("打开悬浮窗权限设置")
        }
        Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
            Text("打开无障碍设置")
        }
        Text(
            text = if (uiState.accessibilityEnabled) {
                "无障碍状态：已开启"
            } else {
                "无障碍状态：未开启（请在系统设置中手动开启 Pet 助手页面感知）"
            },
            style = MaterialTheme.typography.labelMedium
        )
        Button(
            onClick = onShowOverlay,
            enabled = uiState.serviceReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("显示悬浮宠物")
        }
        Button(onClick = onHideOverlay, modifier = Modifier.fillMaxWidth()) {
            Text("隐藏悬浮窗")
        }
        Text(
            text = uiState.statusText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PetEntryContentPreview() {
    MaterialTheme {
        PetEntryContent(
            uiState = PetEntryUiState(
                serviceReady = true,
                overlayVisible = false,
                accessibilityEnabled = false,
                statusText = "状态：预览（无逻辑层 / 无数据层）"
            ),
            onRequestMic = {},
            onRequestCalendar = {},
            onOpenOverlaySettings = {},
            onOpenAccessibilitySettings = {},
            onShowOverlay = {},
            onHideOverlay = {}
        )
    }
}
