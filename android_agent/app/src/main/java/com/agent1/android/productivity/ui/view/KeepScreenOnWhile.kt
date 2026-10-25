package com.agent1.android.productivity.ui.view

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Agent 跑任务时保持屏幕常亮，避免息屏后 Wi‑Fi / 长 SSE 被系统收紧；任务结束自动恢复系统息屏策略。
 */
@Composable
internal fun KeepScreenOnWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        val window = (view.context as? Activity)?.window
        if (active) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
