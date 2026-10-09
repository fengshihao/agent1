package com.agent1.android.productivity.logic.business

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileWatcherTest {

    @Test
    fun firesOnFileModify() {
        val dir = Files.createTempDirectory("watcher-test").toFile()
        val file = dir.resolve("index.html").apply { writeText("<html>1</html>") }
        val latch = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher = WorkspaceFileWatcher(
            file = file,
            debounceMs = 50,
            pollMs = 50,
            callbackDispatcher = Dispatchers.Default,
            scope = scope,
            onChanged = { latch.countDown() },
        )
        try {
            watcher.start()
            file.writeText("<html>2</html>")
            assertTrue("修改后应触发回调", latch.await(5, TimeUnit.SECONDS))
        } finally {
            watcher.stop()
            scope.cancel()
        }
    }

    @Test
    fun debouncesBurstWritesIntoOneCallback() {
        val dir = Files.createTempDirectory("watcher-test").toFile()
        val file = dir.resolve("page.html").apply { writeText("<html>1</html>") }
        var count = 0
        val once = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher = WorkspaceFileWatcher(
            file = file,
            debounceMs = 200,
            pollMs = 50,
            callbackDispatcher = Dispatchers.Default,
            scope = scope,
            onChanged = {
                count++
                once.countDown()
            },
        )
        try {
            watcher.start()
            // 模拟 AI 连续多次 edit_file：突发写只应回调一次
            repeat(5) { i ->
                file.writeText("<html>v$i</html>")
                Thread.sleep(20)
            }
            assertTrue(once.await(5, TimeUnit.SECONDS))
            Thread.sleep(400)
            assertTrue("突发写应防抖为一次回调，实际 $count 次", count == 1)
        } finally {
            watcher.stop()
            scope.cancel()
        }
    }

    @Test
    fun ignoresOtherFilesAndMissingParent() {
        val dir = Files.createTempDirectory("watcher-test").toFile()
        val file = dir.resolve("target.html").apply { writeText("<html>1</html>") }
        val latch = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher = WorkspaceFileWatcher(
            file = file,
            debounceMs = 50,
            pollMs = 50,
            callbackDispatcher = Dispatchers.Default,
            scope = scope,
            onChanged = { latch.countDown() },
        )
        try {
            watcher.start()
            // 同目录其他文件变更不应触发
            dir.resolve("other.html").writeText("<html>x</html>")
            assertTrue("其他文件变更不应触发回调", !latch.await(600, TimeUnit.MILLISECONDS))
            // 目标文件变更触发
            file.writeText("<html>2</html>")
            assertTrue(latch.await(5, TimeUnit.SECONDS))

            // 不存在的父目录 start 不抛异常
            val ghost = WorkspaceFileWatcher(
                file = dir.resolve("nope/ghost.html"),
                debounceMs = 50,
                callbackDispatcher = Dispatchers.Default,
                scope = scope,
                onChanged = {},
            )
            ghost.start()
            ghost.stop()
        } finally {
            watcher.stop()
            scope.cancel()
        }
    }
}