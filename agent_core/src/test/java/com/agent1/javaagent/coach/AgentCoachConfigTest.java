package com.agent1.javaagent.coach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class AgentCoachConfigTest {

    @Test
    void loadsDefaultsFromBootstrapManifest(@TempDir Path agentRoot) {
        AgentHomeBootstrap.ensure(agentRoot);
        AgentCoachConfig config = AgentCoachConfig.load(agentRoot);
        assertTrue(config.enabled());
        assertEquals(65_536, config.largeWriteBytes());
        assertEquals(80, config.inlineLongLines());
        assertEquals(8_192, config.inlineLongBytes());
    }

    @Test
    void readsCustomManifest(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(
            Files.readString(agentRoot.resolve("agent.manifest.json"))
        );
        ObjectNode coach = mapper.createObjectNode();
        coach.put("enabled", false);
        ObjectNode triggers = mapper.createObjectNode();
        triggers.put("fileLargeWriteBytes", 1024);
        triggers.put("scriptInlineLongLines", 10);
        triggers.put("scriptInlineLongBytes", 512);
        coach.set("triggers", triggers);
        root.set("coach", coach);
        Files.writeString(
            agentRoot.resolve("agent.manifest.json"),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
        );

        JsonNode manifest = mapper.readTree(Files.readString(agentRoot.resolve("agent.manifest.json")));
        AgentCoachConfig fromFile = AgentCoachConfig.fromManifest(manifest);
        assertFalse(fromFile.enabled());
        assertEquals(1024, fromFile.largeWriteBytes());
        assertEquals(10, fromFile.inlineLongLines());
        assertEquals(512, fromFile.inlineLongBytes());
    }
}
