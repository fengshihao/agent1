package com.agent1.javaagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentHomeBootstrapTest {

    @TempDir
    Path temp;

    @Test
    void ensureCreatesLayoutAndManifest() throws Exception {
        Path root = temp.resolve("agent-home");
        AgentHomeBootstrap.ensure(root);

        assertTrue(Files.isDirectory(root.resolve("sessions")));
        assertTrue(Files.isDirectory(root.resolve("shared/catalog/scripts")));
        assertTrue(Files.isDirectory(root.resolve("shared/local/skills")));
        assertTrue(Files.isRegularFile(root.resolve("agent.manifest.json")));
        assertTrue(Files.isRegularFile(root.resolve("docs/system/directories.md")));

        JsonNode manifest = new ObjectMapper().readTree(root.resolve("agent.manifest.json").toFile());
        assertEquals(AgentHomeBootstrap.MANIFEST_SCHEMA_VERSION, manifest.path("schemaVersion").asInt());
        assertTrue(manifest.path("coach").path("enabled").asBoolean());
        assertEquals(65_536, manifest.path("coach").path("triggers").path("fileLargeWriteBytes").asInt());
        assertTrue(Files.isRegularFile(root.resolve("docs/system/tools-and-quickjs.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/system/promotion.md")));
    }

    @Test
    void ensureIsIdempotent(@TempDir Path root) throws Exception {
        AgentHomeBootstrap.ensure(root);
        Path manifest = root.resolve("agent.manifest.json");
        String first = Files.readString(manifest);
        AgentHomeBootstrap.ensure(root);
        assertEquals(first, Files.readString(manifest));
    }
}
