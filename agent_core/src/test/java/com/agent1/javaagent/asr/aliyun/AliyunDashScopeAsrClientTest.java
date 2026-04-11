package com.agent1.javaagent.asr.aliyun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.asr.AsrEvent;
import com.agent1.javaagent.asr.AsrStartRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class AliyunDashScopeAsrClientTest {
    @Test
    void startFinish_shouldEmitStartedPartialFinalCompleted() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            List<String> clientTextMessages = new CopyOnWriteArrayList<>();
            CountDownLatch runTaskReceived = new CountDownLatch(1);
            server.enqueue(
                new MockResponse()
                    .withWebSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onMessage(WebSocket webSocket, String text) {
                            clientTextMessages.add(text);
                            if (text.contains("\"action\":\"run-task\"")) {
                                runTaskReceived.countDown();
                                webSocket.send("{\"header\":{\"event\":\"task-started\"}}");
                                webSocket.send(
                                    "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":{\"text\":\"你好\"}}}}"
                                );
                            } else if (text.contains("\"action\":\"finish-task\"")) {
                                webSocket.send("{\"header\":{\"event\":\"task-finished\"}}");
                                webSocket.close(1000, "ok");
                            }
                        }
                    })
            );
            server.start();

            var session = AliyunDashScopeAsrClient.createSession(
                new okhttp3.OkHttpClient(),
                server.url("/ws").toString(),
                new AsrStartRequest("task-1", 16000, "fun-asr-realtime", "test-key")
            );

            CountDownLatch done = new CountDownLatch(1);
            List<AsrEvent> events = new CopyOnWriteArrayList<>();
            var disposable = session.observeEvents().subscribe(
                event -> {
                    events.add(event);
                    if (event instanceof AsrEvent.Completed) {
                        done.countDown();
                    }
                }
            );
            assertTrue(runTaskReceived.await(3, TimeUnit.SECONDS), "expected run-task message");
            session.finish();
            assertTrue(done.await(3, TimeUnit.SECONDS), "expected completed event");

            assertTrue(
                events.stream().anyMatch(e -> e instanceof AsrEvent.Partial && "你好".equals(((AsrEvent.Partial) e).getText()))
            );
            assertTrue(
                events.stream().anyMatch(e -> e instanceof AsrEvent.Final && "你好".equals(((AsrEvent.Final) e).getText()))
            );
            assertTrue(events.stream().anyMatch(e -> e instanceof AsrEvent.Completed));
            assertTrue(clientTextMessages.stream().anyMatch(s -> s.contains("\"action\":\"run-task\"")));
            assertTrue(clientTextMessages.stream().anyMatch(s -> s.contains("\"action\":\"finish-task\"")));

            disposable.dispose();
            session.close();
        }
    }

    @Test
    void cancel_shouldEmitCompletedWithoutFinishTask() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch connected = new CountDownLatch(1);
            List<String> clientTextMessages = new CopyOnWriteArrayList<>();
            server.enqueue(
                new MockResponse()
                    .withWebSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onOpen(WebSocket webSocket, Response response) {
                            connected.countDown();
                        }

                        @Override
                        public void onMessage(WebSocket webSocket, String text) {
                            clientTextMessages.add(text);
                        }
                    })
            );
            server.start();

            var session = AliyunDashScopeAsrClient.createSession(
                new okhttp3.OkHttpClient(),
                server.url("/ws").toString(),
                new AsrStartRequest("task-cancel", 16000, "fun-asr-realtime", "test-key")
            );
            assertTrue(connected.await(3, TimeUnit.SECONDS));

            CountDownLatch done = new CountDownLatch(1);
            var disposable = session.observeEvents().subscribe(event -> {
                if (event instanceof AsrEvent.Completed) {
                    done.countDown();
                }
            });
            session.cancel();
            assertTrue(done.await(3, TimeUnit.SECONDS), "cancel should complete stream");
            assertEquals(
                0,
                clientTextMessages.stream().filter(s -> s.contains("\"action\":\"finish-task\"")).count()
            );

            disposable.dispose();
            session.close();
        }
    }
}
