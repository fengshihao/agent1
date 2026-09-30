package com.agent1.javaagent.agent;

import java.nio.file.Path;

/** Agent 只读文档区逻辑前缀 → 物理目录（与 Weizhi {@code ReadMount} 对齐）。 */
public record AgentReadMount(String logicalPrefix, Path root) {
}
