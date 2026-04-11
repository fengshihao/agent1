package com.dynamicui.demo.pet.logic.data.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.agent1.javaagent.asr.AsrClient
import com.agent1.javaagent.asr.AsrEvent
import com.agent1.javaagent.asr.AsrStartRequest
import com.agent1.javaagent.asr.AsrSession
import com.agent1.javaagent.asr.aliyun.AliyunDashScopeAsrClient
import com.dynamicui.demo.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class DashScopeAsrTransport(
    scope: CoroutineScope,
    runOnMain: (() -> Unit) -> Unit,
    private val asrClient: AsrClient = AliyunDashScopeAsrClient.createClient(BuildConfig.DASHSCOPE_BASE_URL)
) : AsrTransport {
    private val scope = scope
    private val runOnMain = runOnMain
    private var asrSession: AsrSession? = null
    private var asrEventsDisposable: io.reactivex.rxjava3.disposables.Disposable? = null
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var leadingTrimBytesRemaining: Int = 0

    override fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stop(submit = false)
        val apiKey = BuildConfig.DASHSCOPE_API_KEY.trim()
        if (apiKey.isEmpty()) {
            runOnMain { onError("缺少 DASHSCOPE_API_KEY") }
            return
        }
        val taskId = UUID.randomUUID().toString().replace("-", "").take(32)
        leadingTrimBytesRemaining = LEADING_TRIM_BYTES
        val session = asrClient.startSession(
            AsrStartRequest(taskId, SAMPLE_RATE, MODEL_NAME, apiKey)
        )
        asrSession = session
        asrEventsDisposable = session.observeEvents().subscribe(
            { event ->
                when (event) {
                    is AsrEvent.Started -> startRecording(session, onError)
                    is AsrEvent.Partial -> runOnMain { onPartial(event.text) }
                    is AsrEvent.Final -> runOnMain { onFinal(event.text) }
                    is AsrEvent.Error -> runOnMain { onError(event.message) }
                    is AsrEvent.Completed -> {
                        releaseRecorder()
                        clearAsrSubscriptionAndSession()
                    }
                }
            },
            {
                releaseRecorder()
                clearAsrSubscriptionAndSession()
            },
            { clearAsrSubscriptionAndSession() }
        )
    }

    override fun stop(submit: Boolean) {
        recordJob?.cancel()
        recordJob = null
        releaseRecorder()
        val session = asrSession
        if (session == null) {
            asrEventsDisposable?.dispose()
            asrEventsDisposable = null
            return
        }
        if (submit) {
            // 必须先保留订阅再 finish，否则服务端回传的 Final/Completed 无法送达，抬起手指后不会触发提交。
            session.finish()
        } else {
            clearAsrSubscriptionAndSession()
            session.cancel()
        }
    }

    private fun clearAsrSubscriptionAndSession() {
        asrEventsDisposable?.dispose()
        asrEventsDisposable = null
        asrSession = null
    }

    private fun startRecording(
        session: AsrSession,
        onError: (String) -> Unit
    ) {
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
            while (scope.isActive && asrSession === session) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    if (n < 0) break
                    continue
                }
                var start = 0
                if (leadingTrimBytesRemaining > 0) {
                    val drop = minOf(leadingTrimBytesRemaining, n)
                    leadingTrimBytesRemaining -= drop
                    start = drop
                }
                if (start >= n) continue
                val validLen = n - start
                session.sendAudio(buf.copyOfRange(start, start + validLen))
            }
        }
    }

    private fun releaseRecorder() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    private companion object {
        private const val SAMPLE_RATE = 16000
        private const val STREAM_CHUNK_BYTES = 6400
        private const val LEADING_TRIM_MS = 220
        private const val LEADING_TRIM_BYTES = SAMPLE_RATE * 2 * LEADING_TRIM_MS / 1000
        private const val MODEL_NAME = "fun-asr-realtime"
    }
}

