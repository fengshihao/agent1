package com.agent1.javaagent.agent;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** 首次使用 {@code agentRoot} 时创建目录树与 {@code agent.manifest.json}（V0）。 */
public final class AgentHomeBootstrap {

    public static final int MANIFEST_SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> DIRECTORY_SUFFIXES = List.of(
        "sessions",
        "logs",
        "sync",
        "docs/system",
        "docs/capabilities",
        "shared/catalog/skills",
        "shared/catalog/scripts",
        "shared/catalog/libs/js",
        "shared/catalog/libs/qjs",
        "shared/catalog/assets/images",
        "shared/catalog/assets/data",
        "shared/catalog/native",
        "shared/catalog/bundles",
        "shared/local/skills",
        "shared/local/scripts"
    );

    private AgentHomeBootstrap() {
    }

    public static void ensure(Path agentRoot) {
        Path root = agentRoot.toAbsolutePath().normalize();
        for (String suffix : DIRECTORY_SUFFIXES) {
            try {
                Files.createDirectories(root.resolve(suffix));
            } catch (IOException e) {
                throw new IllegalStateException("create agent home dir failed: " + root.resolve(suffix), e);
            }
        }
        ensureManifest(root);
        ensureBundledSystemDocs(root);
    }

    private static void ensureManifest(Path root) {
        Path manifest = root.resolve("agent.manifest.json");
        if (Files.isRegularFile(manifest)) {
            return;
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("schemaVersion", MANIFEST_SCHEMA_VERSION);
        node.put("createdAt", Instant.now().toString());
        node.put("agentRoot", root.toString());
        node.put("note", "Agent1 运行时家目录；规划见 doc/规划/自进化Agent/");
        ObjectNode coach = MAPPER.createObjectNode();
        coach.put("enabled", true);
        ObjectNode triggers = MAPPER.createObjectNode();
        triggers.put("fileLargeWriteBytes", 65_536);
        triggers.put("scriptInlineLongLines", 80);
        triggers.put("scriptInlineLongBytes", 8_192);
        triggers.put("scriptFailRepeat", 3);
        coach.set("triggers", triggers);
        node.set("coach", coach);
        try {
            PathIo.writeString(
                manifest,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node),
                StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException("write manifest failed: " + manifest, e);
        }
    }

    private static void ensureBundledSystemDocs(Path root) {
        Path systemDir = root.resolve("docs/system");
        copyResourceIfMissing(systemDir.resolve("README.md"), "/agent-home/docs/system/README.md");
        copyResourceIfMissing(systemDir.resolve("directories.md"), "/agent-home/docs/system/directories.md");
        copyResourceIfMissing(systemDir.resolve("catalog-install.md"), "/agent-home/docs/system/catalog-install.md");
        copyResourceIfMissing(systemDir.resolve("promotion.md"), "/agent-home/docs/system/promotion.md");
        copyResourceIfMissing(systemDir.resolve("trusted-sources.md"), "/agent-home/docs/system/trusted-sources.md");
        copyResourceIfMissing(
            systemDir.resolve("tools-and-quickjs.md"),
            "/agent-home/docs/system/tools-and-quickjs.md"
        );
    }

    private static void copyResourceIfMissing(Path target, String resourcePath) {
        if (Files.isRegularFile(target)) {
            return;
        }
        try (InputStream in = AgentHomeBootstrap.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("copy bundled doc failed: " + target, e);
        }
    }
}
