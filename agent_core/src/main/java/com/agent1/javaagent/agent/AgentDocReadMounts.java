package com.agent1.javaagent.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** agentRoot 下 docs/system、docs/capabilities 只读挂载（grep/glob/read_file 共用）。 */
public final class AgentDocReadMounts {

    private AgentDocReadMounts() {
    }

    public static List<AgentReadMount> forAgentRoot(Path agentRoot) {
        if (agentRoot == null) {
            return List.of();
        }
        Path root = agentRoot.toAbsolutePath().normalize();
        List<AgentReadMount> out = new ArrayList<>(2);
        addIfExists(out, "docs/system", root.resolve("docs/system"));
        addIfExists(out, "docs/capabilities", root.resolve("docs/capabilities"));
        return List.copyOf(out);
    }

    private static void addIfExists(List<AgentReadMount> out, String prefix, Path dir) {
        if (Files.isDirectory(dir)) {
            out.add(new AgentReadMount(prefix, dir));
        }
    }
}
