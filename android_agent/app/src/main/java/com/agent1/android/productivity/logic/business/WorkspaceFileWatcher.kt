package com.agent1.android.productivity.logic.business

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 监听 workspace 内单个文件变更（18 号规划 Phase C / REQ-123：预览热更新）。
 *
 * mtime + length 轮询实现：跨平台（Android/Linux/macOS JVM）行为一致且可测。
 * 相比 WatchService（macOS JVM 为约 10s 间隔的轮询实现）感知粒度更均匀；
 * 1s 轮询对「AI 修改 → 预览刷新」的秒级体验足够。
 * 命中变更后防抖 [debounceMs] 再回调一次：AI 连续多次 edit_file 写同一文件时
 * 避免逐次 reload。回调在 [callbackDispatcher] 上执行（默认主线程，触发 WebView reload）。
 */
class WorkspaceFileWatcher(
    private val file: File,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val pollMs: Long = DEFAULT_POLL_MS,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    private var loopJob: Job? = null
    private var pendingJob: Job? = null
    private var lastModifiedAt: Long = -1L
    private var lastLength: Long = -1L

    fun start() {
        if (loopJob?.isActive == true) return
        if (!file.isFile) return
        lastModifiedAt = runCatching { file.lastModified() }.getOrDefault(0L)
        lastLength = runCatching { file.length() }.getOrDefault(0L)
        loopJob = scope.launch(Dispatchers.IO) { watchLoop() }
    }

    fun stop() {
        pendingJob?.cancel()
        pendingJob = null
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun watchLoop() {
        while (scope.isActive) {
            delay(pollMs)
            // 文件被删除时 lastModified 返回 0，同样触发回调（reload 会给出错误态）
            val modified = runCatching { file.lastModified() }.getOrDefault(0L)
            val length = runCatching { file.length() }.getOrDefault(0L)
            if (modified != lastModifiedAt || length != lastLength) {
                lastModifiedAt = modified
                lastLength = length
                // 防抖：合并 AI 连续写，稳定后回调一次
                pendingJob?.cancel()
                pendingJob = scope.launch(callbackDispatcher) {
                    delay(debounceMs)
                    onChanged()
                }
            }
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 300L
        const val DEFAULT_POLL_MS = 1_000L
    }
}