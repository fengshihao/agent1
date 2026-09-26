package com.agent1.javaagent.weizhi.desktop.cdp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** 最小 CDP WebSocket 客户端（请求/响应 id 匹配 + 事件分发）。 */
public final class CdpJsonRpcClient implements AutoCloseable {

    private final WebSocket webSocket;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, PendingCall> pending = new ConcurrentHashMap<>();
    private volatile Consumer<JsonObject> eventHandler = event -> {
    };

    public CdpJsonRpcClient(String webSocketDebuggerUrl) throws IOException {
        OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();
        Request request = new Request.Builder().url(webSocketDebuggerUrl).build();
        WebSocket[] holder = new WebSocket[1];
        holder[0] = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                failAll(t);
            }
        });
        this.webSocket = holder[0];
    }

    public void setEventHandler(Consumer<JsonObject> handler) {
        this.eventHandler = handler == null ? event -> {
        } : handler;
    }

    public JsonObject call(String method, JsonObject params, long timeoutMs) throws Exception {
        int id = nextId.getAndIncrement();
        JsonObject req = new JsonObject();
        req.addProperty("id", id);
        req.addProperty("method", method);
        req.add("params", params == null ? new JsonObject() : params);
        PendingCall call = new PendingCall();
        pending.put(id, call);
        if (!webSocket.send(req.toString())) {
            pending.remove(id);
            throw new IOException("CDP WebSocket 发送失败: " + method);
        }
        JsonObject resp = call.await(timeoutMs);
        if (resp.has("error")) {
            throw new IOException("CDP " + method + " 失败: " + resp.get("error"));
        }
        return resp.has("result") ? resp.getAsJsonObject("result") : new JsonObject();
    }

    private void handleMessage(String text) {
        JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
        if (msg.has("id") && !msg.get("id").isJsonNull()) {
            PendingCall call = pending.remove(msg.get("id").getAsInt());
            if (call != null) {
                call.complete(msg);
            }
            return;
        }
        if (msg.has("method")) {
            eventHandler.accept(msg);
        }
    }

    private void failAll(Throwable t) {
        for (PendingCall call : pending.values()) {
            call.fail(t);
        }
        pending.clear();
    }

    @Override
    public void close() {
        webSocket.close(1000, "done");
    }

    private static final class PendingCall {
        private final Object lock = new Object();
        private JsonObject result;
        private Throwable error;

        JsonObject await(long timeoutMs) throws Exception {
            synchronized (lock) {
                long deadline = System.currentTimeMillis() + timeoutMs;
                while (result == null && error == null) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        throw new TimeoutException("CDP 调用超时");
                    }
                    lock.wait(wait);
                }
                if (error != null) {
                    if (error instanceof Exception e) {
                        throw e;
                    }
                    throw new Exception(error);
                }
                return result;
            }
        }

        void complete(JsonObject value) {
            synchronized (lock) {
                result = value;
                lock.notifyAll();
            }
        }

        void fail(Throwable t) {
            synchronized (lock) {
                error = t;
                lock.notifyAll();
            }
        }
    }
}
