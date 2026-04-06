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
    private var currentTaskId: String? = null
    private val resultBuffer = StringBuilder()

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
        resultBuffer.clear()
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
                                // 服务端多数情况下返回“当前句累计文本”，这里覆盖而非追加，避免重复叠字。
                                resultBuffer.setLength(0)
                                resultBuffer.append(t)
                                val snap = resultBuffer.toString()
                                runOnMain { onPartial(snap) }
                            }
                        }
                        "task-finished" -> {
                            webSocket.close(1000, null)
                            val out = resultBuffer.toString()
                            runOnMain { onFinal(out) }
                        }
                        "task-failed" -> {
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
                if (!stopped.get()) {
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
            val buf = ByteArray(3200)
            while (scope.isActive && !stopped.get()) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    try {
                        webSocket.send(buf.copyOfRange(0, n).toByteString())
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
        stopped.set(true)
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
            val finish = buildFinishTaskMessageText(taskId)
            try {
                ws.send(finish)
            } catch (_: Exception) {
            }
        } else {
            try {
                ws.cancel()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "DashScopeFunAsr"
        private const val SAMPLE_RATE = 16000

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
