package com.agent1.javaagent.asr;

import io.reactivex.rxjava3.core.Observable;

public interface AsrSession extends AutoCloseable {
    Observable<AsrEvent> observeEvents();

    void sendAudio(byte[] pcmChunk);

    void finish();

    void cancel();

    @Override
    void close();
}
