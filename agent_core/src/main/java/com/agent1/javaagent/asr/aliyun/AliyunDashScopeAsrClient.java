package com.agent1.javaagent.asr.aliyun;

import com.agent1.javaagent.asr.AsrClient;
import com.agent1.javaagent.asr.AsrEvent;
import com.agent1.javaagent.asr.AsrSession;
import com.agent1.javaagent.asr.AsrStartRequest;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class AliyunDashScopeAsrClient {
    private AliyunDashScopeAsrClient() {}

    public static AsrClient createClient(String dashScopeBaseUrl) {
        return new AsrClient() {
            @Override
            public AsrSession startSession(AsrStartRequest request) {
                return createSession(dashScopeBaseUrl, request);
            }

            @Override
            public void close() {
                // Sessions own OkHttp lifecycle; nothing to release at client level.
            }
        };
    }

    public static AsrSession createSession(String dashScopeBaseUrl, AsrStartRequest request) {
        OkHttpClient client = new OkHttpClient();
        return new Session(client, wsUrlForBase(dashScopeBaseUrl), request, true);
    }

    public static AsrSession createSession(OkHttpClient httpClient, String wsUrl, AsrStartRequest request) {
        return new Session(
            Objects.requireNonNull(httpClient, "httpClient"),
            Objects.requireNonNull(wsUrl, "wsUrl"),
            Objects.requireNonNull(request, "request"),
            false
        );
    }

    static String wsUrlForBase(String baseUrl) {
        if (baseUrl != null && baseUrl.toLowerCase().contains("intl")) {
            return "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference/";
        }
        return "wss://dashscope.aliyuncs.com/api-ws/v1/inference/";
    }

    private static final class Session implements AsrSession {
        private final AsrStartRequest request;
        private final OkHttpClient httpClient;
        private final boolean ownsHttpClient;
        private final Subject<AsrEvent> eventSubject = ReplaySubject.<AsrEvent>create().toSerialized();
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final StringBuilder resultBuffer = new StringBuilder();
        private final AtomicBoolean httpClientReleased = new AtomicBoolean(false);
        private volatile WebSocket webSocket;

        private Session(OkHttpClient httpClient, String wsUrl, AsrStartRequest request, boolean ownsHttpClient) {
            this.request = Objects.requireNonNull(request, "request");
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            this.ownsHttpClient = ownsHttpClient;
            Request wsRequest = new Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "bearer " + request.getApiKey())
                .build();
            this.webSocket = httpClient.newWebSocket(
                wsRequest,
                new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        String runTask = DashScopeProtocol.buildRunTaskMessageText(
                            request.getTaskId(),
                            request.getSampleRate(),
                            request.getModel()
                        );
                        webSocket.send(runTask);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        try {
                            var root = DashScopeProtocol.parseJson(text);
                            String event = DashScopeProtocol.event(root);
                            if ("task-started".equals(event)) {
                                eventSubject.onNext(new AsrEvent.Started());
                            } else if ("result-generated".equals(event)) {
                                String incoming = DashScopeProtocol.sentenceText(root).trim();
                                if (!incoming.isEmpty()) {
                                    String merged = mergeStreamingText(resultBuffer.toString(), incoming);
                                    resultBuffer.setLength(0);
                                    resultBuffer.append(merged);
                                    eventSubject.onNext(new AsrEvent.Partial(merged));
                                }
                            } else if ("task-finished".equals(event)) {
                                eventSubject.onNext(new AsrEvent.Final(resultBuffer.toString()));
                                emitCompleted();
                                webSocket.close(1000, null);
                            } else if ("task-failed".equals(event)) {
                                eventSubject.onNext(new AsrEvent.Error(DashScopeProtocol.errorMessage(root)));
                                emitCompleted();
                            }
                        } catch (Exception e) {
                            eventSubject.onNext(new AsrEvent.Error(e.getMessage() == null ? "解析识别结果失败" : e.getMessage()));
                            emitCompleted();
                        }
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        if (!aborted.get()) {
                            String msg = t == null || t.getMessage() == null ? "语音连接失败" : t.getMessage();
                            eventSubject.onNext(new AsrEvent.Error(msg));
                        }
                        emitCompleted();
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        emitCompleted();
                    }
                }
            );
        }

        @Override
        public Observable<AsrEvent> observeEvents() {
            return eventSubject;
        }

        @Override
        public void sendAudio(byte[] pcmChunk) {
            WebSocket ws = this.webSocket;
            if (ws != null && pcmChunk != null && pcmChunk.length > 0 && !completed.get()) {
                ws.send(ByteString.of(pcmChunk, 0, pcmChunk.length));
            }
        }

        @Override
        public void finish() {
            WebSocket ws = this.webSocket;
            if (ws != null && !completed.get()) {
                ws.send(DashScopeProtocol.buildFinishTaskMessageText(request.getTaskId()));
            }
        }

        @Override
        public void cancel() {
            aborted.set(true);
            WebSocket ws = this.webSocket;
            this.webSocket = null;
            if (ws != null) {
                ws.cancel();
            }
            emitCompleted();
        }

        @Override
        public void close() {
            cancel();
        }

        private void emitCompleted() {
            if (completed.compareAndSet(false, true)) {
                eventSubject.onNext(new AsrEvent.Completed());
                eventSubject.onComplete();
                releaseOwnedClientIfNeeded();
            }
        }

        private void releaseOwnedClientIfNeeded() {
            if (!ownsHttpClient || !httpClientReleased.compareAndSet(false, true)) {
                return;
            }
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            if (httpClient.cache() != null) {
                try {
                    httpClient.cache().close();
                } catch (IOException ignored) {
                    // ignore cache close failure
                }
            }
        }

        private static String mergeStreamingText(String previous, String incoming) {
            String prev = previous.trim();
            String next = incoming.trim();
            if (prev.isEmpty()) {
                return next;
            }
            if (next.isEmpty()) {
                return prev;
            }
            if (next.startsWith(prev)) {
                return next;
            }
            if (prev.startsWith(next)) {
                return prev;
            }
            if (prev.contains(next)) {
                return prev;
            }
            if (next.contains(prev)) {
                return next;
            }
            int overlap = maxPrefixSuffixOverlap(prev, next);
            if (overlap > 0) {
                return prev + next.substring(overlap);
            }
            return prev + next;
        }

        private static int maxPrefixSuffixOverlap(String a, String b) {
            int max = Math.min(a.length(), b.length());
            for (int len = max; len >= 1; len--) {
                if (a.regionMatches(a.length() - len, b, 0, len)) {
                    return len;
                }
            }
            return 0;
        }
    }
}
