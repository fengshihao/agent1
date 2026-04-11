package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import java.util.List;

@FunctionalInterface
public interface ContextTransformer {
    List<AgentMessage> transform(List<AgentMessage> messages);
}
