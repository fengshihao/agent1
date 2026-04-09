package com.dynamicui.demo.pet.logic.data.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.dynamicui.demo.BuildConfig
import com.dynamicui.demo.pet.logic.data.service.AgentFileLogger
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
        Log.i(TAG, "start asr taskId=$taskId")
        AgentFileLogger.log(TAG, "start taskId=$taskId")

        val request = Request.Builder()
            .url(wsUrl())
            .addHeader("Authorization", "bearer $apiKey")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws open, send run-task taskId=$taskId")
                AgentFileLogger.log(TAG, "ws open taskId=$taskId")
                val runTask = buildRunTaskMessageText(taskId, SAMPLE_RATE)
                webSocket.send(runTask)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jo = JSONObject(text)
                    val header = jo.optJSONObject("header") ?: return
                    val event = header.optString("event", "")
                    if (event.isNotBlank()) {
                        Log.i(TAG, "ws event=$event")
                        AgentFileLogger.log(TAG, "ws event=$event")
                    }
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
                                // 实时阶段采用“只增不减”的合并，避免停顿后新分句冲掉前半段内容。
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
                            Log.i(TAG, "task-finished finalLen=${out.length}")
                            AgentFileLogger.log(TAG, "task-finished finalLen=${out.length}")
                            runOnMain { onFinal(out) }
                        }
                        "task-failed" -> {
                            stopped.set(true)
                            val err = header.optString("error_message", "语音识别失败")
                            Log.e(TAG, "task-failed: $err raw=$text")
                            AgentFileLogger.log(TAG, "task-failed: $err")
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
                    AgentFileLogger.log(TAG, "ws parse error: ${e.message ?: "unknown"}")
                    runOnMain { onError(e.message ?: "解析识别结果失败") }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!aborted.get()) {
                    val msg = t.message ?: "语音连接失败"
                    Log.e(TAG, "ws failure msg=$msg code=${response?.code}", t)
                    AgentFileLogger.log(TAG, "ws failure msg=$msg code=${response?.code ?: -1}")
                    runOnMain { onError(msg) }
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
            AgentFileLogger.log(TAG, "AudioRecord not initialized")
            runOnMain { onError("麦克风未就绪") }
            record.release()
            return
        }
        audioRecord = record
        try {
            record.startRecording()
        } catch (e: Exception) {
            AgentFileLogger.log(TAG, "startRecording failed: ${e.message ?: "unknown"}")
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
            AgentFileLogger.log(TAG, "stop submit=true taskId=$taskId")
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
            AgentFileLogger.log(TAG, "stop submit=false")
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

        private fun mergeStreamingText(previous: String, incoming: String): String {
            val prev = previous.trim()
            val next = incoming.trim()
            if (prev.isEmpty()) return next
            if (next.isEmpty()) return prev
            if (next.startsWith(prev)) return next
            if (prev.startsWith(next)) return prev
            if (prev.contains(next)) return prev
            if (next.contains(prev)) return next
            val overlap = maxPrefixSuffixOverlap(prev, next)
            if (overlap > 0) return prev + next.drop(overlap)
            return prev + next
        }

        private fun maxPrefixSuffixOverlap(a: String, b: String): Int {
            val max = minOf(a.length, b.length)
            for (len in max downTo 1) {
                if (a.regionMatches(a.length - len, b, 0, len, ignoreCase = false)) return len
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

