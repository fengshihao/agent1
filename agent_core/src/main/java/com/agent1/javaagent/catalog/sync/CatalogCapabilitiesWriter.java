package com.agent1.javaagent.catalog.sync;

import com.agent1.javaagent.catalog.CatalogItem;
import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** sync 成功后追加 capabilities 索引（阶段 5.6，轻量 markdown）。 */
public final class CatalogCapabilitiesWriter {

    private CatalogCapabilitiesWriter() {
    }

    public static void writeOrUpdate(Path agentRoot, CatalogItem item, String relativePath) throws IOException {
        Path dir = agentRoot.resolve("docs/capabilities");
        Files.createDirectories(dir);
        String fileName = safeFileName(item.id()) + ".md";
        Path target = dir.resolve(fileName);
        String body = ""
            + "# " + item.id() + "\n\n"
            + "- kind: `" + item.kind() + "`\n"
            + "- version: `" + item.version() + "`\n"
            + "- digest: `" + item.digest() + "`\n"
            + "- path: `" + relativePath + "`\n"
            + "- updatedAt: `" + Instant.now() + "`\n\n"
            + "Installed via catalog sync. See `docs/system/catalog-install.md`.\n";
        PathIo.writeString(target, body);
    }

    private static String safeFileName(String id) {
        return id.replace('/', '_').replace('\\', '_');
    }
}
