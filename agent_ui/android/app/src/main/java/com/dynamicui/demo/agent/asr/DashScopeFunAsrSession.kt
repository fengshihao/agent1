package com.dynamicui.demo.agent.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.dynamicui.demo.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot Fun-ASR realtime session (DashScope WebSocket). Protocol matches public Model Studio docs.
 */
class DashScopeFunAsrSession(
    private val scope: CoroutineScope,
    private val runOnMain: (() -> Unit) -> Unit
) {

    private val client = OkHttpClient()
    private var socket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val stopped = AtomicBoolean(false)
    private val aborted = AtomicBoolean(false)
    private var currentTaskId: String? = null
    private val resultBuffer = StringBuilder()
    private val capturedAudio = ByteArrayOutputStream()
    private var leadingTrimBytesRemaining: Int = 0

    private val apiKey: String
        get() = BuildConfig.DASHSCOPE_API_KEY.trim()

    private fun wsUrl(): String {
        val b = BuildConfig.DASHSCOPE_BASE_URL
        return if (b.contains("intl", ignoreCase = true)) {
            "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference/"
        } else {
            "wss://dashscope.aliyuncs.com/api-ws/v1/inference/"
        }
    }

    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopped.set(false)
        aborted.set(false)
        resultBuffer.clear()
        synchronized(capturedAudio) {
            capturedAudio.reset()
        }
        leadingTrimBytesRemaining = LEADING_TRIM_BYTES
        currentTaskId = null
        if (apiKey.isEmpty()) {
            runOnMain { onError("缺少 DASHSCOPE_API_KEY") }
            return
        }
        val taskId = UUID.randomUUID().toString().replace("-", "").take(32)
        currentTaskId = taskId

        val request = Request.Builder()
            .url(wsUrl())
            .addHeader("Authorization", "bearer $apiKey")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val runTask = buildRunTaskMessageText(taskId, SAMPLE_RATE)
                webSocket.send(runTask)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jo = JSONObject(text)
                    val header = jo.optJSONObject("header") ?: return
                    val event = header.optString("event", "")
                    when (event) {
                        "task-started" -> {
                            runOnMain { startRecording(webSocket, onError) }
                        }
                        "result-generated" -> {
                            val sentence = jo.optJSONObject("payload")
                                ?.optJSONObject("output")
                                ?.optJSONObject("sentence") ?: return
                            val t = sentence.optString("text", "")
                            if (t.isNotBlank()) {
                                val merged = mergeStreamingText(resultBuffer.toString(), t)
                                resultBuffer.setLength(0)
                                resultBuffer.append(merged)
                                val snap = resultBuffer.toString()
                                runOnMain { onPartial(snap) }
                            }
                        }
                        "task-finished" -> {
                            stopped.set(true)
                            webSocket.close(1000, null)
                            val out = resultBuffer.toString()
                            runOnMain { onFinal(out) }
                        }
                        "task-failed" -> {
                            stopped.set(true)
                            val err = header.optString("error_message", "语音识别失败")
                            Log.e(TAG, "task-failed: $err raw=$text")
                            runOnMain { onError(err) }
                        }
                        else -> {
                            if (event.isNotBlank()) {
                                Log.w(TAG, "unhandled event=$event raw=$text")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ws text parse raw=$text", e)
                    runOnMain { onError(e.message ?: "解析识别结果失败") }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!aborted.get()) {
                    runOnMain { onError(t.message ?: "语音连接失败") }
                }
            }
        })
    }

    private fun startRecording(webSocket: WebSocket, onError: (String) -> Unit) {
        if (stopped.get()) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            runOnMain { onError("无法初始化录音缓冲区") }
            return
        }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            runOnMain { onError(e.message ?: "AudioRecord 创建失败") }
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runOnMain { onError("麦克风未就绪") }
            record.release()
            return
        }
        audioRecord = record
        try {
            record.startRecording()
        } catch (e: Exception) {
            runOnMain { onError(e.message ?: "无法开始录音") }
            record.release()
            audioRecord = null
            return
        }

        recordJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(STREAM_CHUNK_BYTES)
            while (scope.isActive && !stopped.get()) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    var start = 0
                    if (leadingTrimBytesRemaining > 0) {
                        val drop = minOf(leadingTrimBytesRemaining, n)
                        leadingTrimBytesRemaining -= drop
                        start = drop
                    }
                    if (start >= n) continue
                    val validLen = n - start
                    synchronized(capturedAudio) {
                        capturedAudio.write(buf, start, validLen)
                    }
                    try {
                        if (REALTIME_STREAMING_ENABLED) {
                            webSocket.send(buf.copyOfRange(start, n).toByteString())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "send chunk", e)
                        break
                    }
                } else if (n < 0) {
                    break
                }
            }
        }
    }

    /**
     * @param submit true: send finish-task and wait for server; false: abort connection (cancel / swipe-up).
     */
    fun stop(submit: Boolean) {
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        val ws = socket
        socket = null
        val taskId = currentTaskId
        currentTaskId = null
        if (ws == null) return
        if (submit && taskId != null) {
            if (!REALTIME_STREAMING_ENABLED) {
                val audio = synchronized(capturedAudio) { capturedAudio.toByteArray() }
                var offset = 0
                while (offset < audio.size) {
                    val end = minOf(offset + STREAM_CHUNK_BYTES, audio.size)
                    try {
                        ws.send(audio.copyOfRange(offset, end).toByteString())
                    } catch (_: Exception) {
                        stopped.set(true)
                        aborted.set(true)
                        ws.cancel()
                        return
                    }
                    offset = end
                }
            }
            val finish = buildFinishTaskMessageText(taskId)
            try {
                ws.send(finish)
            } catch (_: Exception) {
                stopped.set(true)
                aborted.set(true)
                ws.cancel()
            }
        } else {
            stopped.set(true)
            aborted.set(true)
            try {
                ws.cancel()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "DashScopeFunAsr"
        private const val SAMPLE_RATE = 16000
        // 实时字幕默认开启。
        private const val REALTIME_STREAMING_ENABLED = true
        // 丢弃起录瞬间的短噪声，降低首字被误识别成“对/嗯”等语气词的概率。
        private const val LEADING_TRIM_MS = 220
        private const val LEADING_TRIM_BYTES = SAMPLE_RATE * 2 * LEADING_TRIM_MS / 1000
        private const val STREAM_CHUNK_BYTES = 6400 // 200ms @ 16kHz, 16-bit, mono
        private val LEADING_FILLER_TOKENS = setOf("对", "嗯", "呃", "啊", "唉")

        /**
         * 兼容两类服务端返回：
         * 1) 累计文本（新串以前缀包含旧串）
         * 2) 断句后只返回新片段（停顿后开始新句）
         */
        internal fun mergeStreamingText(previous: String, incoming: String): String {
            val prev = previous.trim()
            val next = incoming.trim()
            if (prev.isEmpty()) return next
            if (next.isEmpty()) return prev
            // 首段极短识别（常见为单字噪声）允许被后续更长文本直接替换纠正。
            if (prev.length <= 1 && next.length >= 2) return next
            if (prev in LEADING_FILLER_TOKENS && !next.startsWith(prev) && next.length >= 2) return next
            if (next.startsWith(prev)) return next
            if (prev.startsWith(next)) return prev
            if (prev.contains(next)) return prev
            if (next.contains(prev)) return next

            val overlap = maxPrefixSuffixOverlap(prev, next)
            if (overlap > 0) {
                return prev + next.drop(overlap)
            }
            return prev + next
        }

        private fun maxPrefixSuffixOverlap(a: String, b: String): Int {
            val max = minOf(a.length, b.length)
            for (len in max downTo 1) {
                if (a.regionMatches(a.length - len, b, 0, len, ignoreCase = false)) {
                    return len
                }
            }
            return 0
        }

        internal fun buildRunTaskMessageText(taskId: String, sampleRate: Int): String {
            return """
                {
                  "header": {
                    "action": "run-task",
                    "task_id": "$taskId",
                    "streaming": "duplex"
                  },
                  "payload": {
                    "task_group": "audio",
                    "task": "asr",
                    "function": "recognition",
                    "model": "fun-asr-realtime",
                    "parameters": {
                      "format": "pcm",
                      "sample_rate": $sampleRate
                    },
                    "input": {}
                  }
                }
            """.trimIndent()
        }

        internal fun buildFinishTaskMessageText(taskId: String): String {
            return """
                {
                  "header": {
                    "action": "finish-task",
                    "task_id": "$taskId",
                    "streaming": "duplex"
                  },
                  "payload": {
                    "input": {}
                  }
                }
            """.trimIndent()
        }
    }
}
