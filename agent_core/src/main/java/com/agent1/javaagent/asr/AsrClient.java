package com.agent1.javaagent.asr;

public interface AsrClient extends AutoCloseable {
    AsrSession startSession(AsrStartRequest request);

    @Override
    void close();
}
