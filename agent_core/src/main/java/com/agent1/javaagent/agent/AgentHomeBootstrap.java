package com.agent1.javaagent.agent;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.util.PathIo;
import com.agent1.javaagent.catalog.OfficeCatalogScripts;
import com.agent1.javaagent.catalog.SvgRasterCatalogScripts;
import com.agent1.javaagent.catalog.WebLibCatalogTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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
        "shared/catalog/templates",
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
        removeBundledSystemDocs(root);
        OfficeCatalogScripts.ensure(root);
        SvgRasterCatalogScripts.ensure(root);
        WebLibCatalogTemplates.ensure(root);
        CapabilityIndexStore.ensure(root);
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
        ObjectNode catalog = MAPPER.createObjectNode();
        catalog.put("manifestUrl", "");
        catalog.put("note", "或使用环境变量 AGENT1_CATALOG_MANIFEST_URL");
        node.set("catalog", catalog);
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

    /** 旧版本曾把 API 手册拷进 docs/system。能力改走调用卡后，升级时删掉这些文件。 */
    private static final List<String> RETIRED_SYSTEM_DOCS = List.of(
        "README.md",
        "directories.md",
        "catalog-install.md",
        "promotion.md",
        "trusted-sources.md",
        "tools-and-quickjs.md",
        "events-audit.md",
        "office-docx.md",
        "svg-raster.md",
        "android-intent.md"
    );

    private static void removeBundledSystemDocs(Path root) {
        Path systemDir = root.resolve("docs/system");
        for (String name : RETIRED_SYSTEM_DOCS) {
            try {
                Files.deleteIfExists(systemDir.resolve(name));
            } catch (IOException e) {
                throw new IllegalStateException("remove retired system doc failed: " + name, e);
            }
        }
    }
}
