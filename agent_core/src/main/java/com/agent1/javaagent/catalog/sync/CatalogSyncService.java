package com.agent1.javaagent.catalog.sync;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.CatalogDigest;
import com.agent1.javaagent.catalog.CatalogIndex;
import com.agent1.javaagent.catalog.CatalogItem;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Manifest diff + HTTP 拉取 + 落盘（阶段 5.2–5.4）。 */
public final class CatalogSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    private final OkHttpClient http;

    public CatalogSyncService(Path agentRoot) {
        this(agentRoot, new OkHttpClient());
    }

    public CatalogSyncService(Path agentRoot, OkHttpClient http) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.http = http;
    }

    public SyncCheckResult check() throws IOException {
        AgentHomeBootstrap.ensure(agentRoot);
        String manifestUrl = requireManifestUrl();
        CatalogIndex index = fetchManifest(manifestUrl);
        cacheRemoteManifest(index, manifestUrl);
        SyncState state = SyncState.load(agentRoot);
        List<CatalogSyncDiff.PendingItem> pending = CatalogSyncDiff.pending(index, state);
        writePendingJson(pending);
        SyncState updated = state.withCheckTime(manifestUrl, Instant.now());
        updated.save(agentRoot);
        return new SyncCheckResult(manifestUrl, index.catalogId(), pending, CatalogSyncDiff.pendingCountByKind(pending));
    }

    public SyncApplyResult apply(List<String> ids) throws IOException {
        AgentHomeBootstrap.ensure(agentRoot);
        String manifestUrl = requireManifestUrl();
        CatalogIndex index = fetchManifest(manifestUrl);
        SyncState state = SyncState.load(agentRoot);
        List<CatalogSyncDiff.PendingItem> pending = CatalogSyncDiff.pending(index, state);
        List<CatalogSyncDiff.PendingItem> toApply = filterPending(pending, ids);
        if (toApply.isEmpty()) {
            return new SyncApplyResult(manifestUrl, List.of(), List.of(), state.items());
        }
        Map<String, SyncState.InstalledItem> installed = new LinkedHashMap<>(state.items());
        List<String> appliedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (CatalogSyncDiff.PendingItem entry : toApply) {
            try {
                SyncState.InstalledItem record = installOne(index, entry.item());
                installed.put(entry.item().id(), record);
                appliedIds.add(entry.item().id());
                CatalogCapabilitiesWriter.writeOrUpdate(agentRoot, entry.item(), record.relativePath());
            } catch (Exception e) {
                errors.add(entry.item().id() + ": " + e.getMessage());
            }
        }
        SyncState updated = state.withApplyTime(Instant.now(), installed);
        updated = updated.withCheckTime(manifestUrl, Instant.now());
        updated.save(agentRoot);
        writePendingJson(CatalogSyncDiff.pending(index, updated));
        return new SyncApplyResult(manifestUrl, appliedIds, errors, installed);
    }

    private List<CatalogSyncDiff.PendingItem> filterPending(
        List<CatalogSyncDiff.PendingItem> pending,
        List<String> ids
    ) {
        if (ids == null || ids.isEmpty()) {
            return pending;
        }
        List<CatalogSyncDiff.PendingItem> out = new ArrayList<>();
        for (CatalogSyncDiff.PendingItem entry : pending) {
            if (ids.contains(entry.item().id())) {
                out.add(entry);
            }
        }
        return out;
    }

    private SyncState.InstalledItem installOne(CatalogIndex index, CatalogItem item) throws IOException {
        byte[] bytes = downloadItemBytes(index, item);
        if (!CatalogDigest.matches(item.digest(), bytes)) {
            throw new IOException("digest mismatch for " + item.id());
        }
        Path target = CatalogInstallPaths.catalogFile(agentRoot, item);
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("invalid catalog path for " + item.id());
        }
        Files.createDirectories(parent);
        Path installedPath;
        if (isZipItem(item)) {
            installedPath = parent.resolve(stripZipSegment(item));
            unzipToCatalog(item, bytes, parent);
        } else {
            Files.write(target, bytes);
            installedPath = target;
        }
        String relative = CatalogInstallPaths.relativeFromAgentRoot(agentRoot, installedPath);
        return new SyncState.InstalledItem(item.version(), item.digest(), Instant.now().toString(), relative);
    }

    private static boolean isZipItem(CatalogItem item) {
        String path = item.path().toLowerCase();
        return path.endsWith(".zip")
            && ("bundle".equals(item.kind()) || "skill".equals(item.kind()) || "image".equals(item.kind()));
    }

    private static String stripZipSegment(CatalogItem item) {
        var fileName = Path.of(item.path()).getFileName();
        String name = fileName == null ? item.id() : fileName.toString();
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private void unzipToCatalog(CatalogItem item, byte[] zipBytes, Path destDir) throws IOException {
        Path extractRoot = destDir.resolve(stripZipSegment(item));
        Files.createDirectories(extractRoot);
        try (InputStream in = new ByteArrayInputStream(zipBytes);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(extractRoot.resolve(entry.getName()));
                    continue;
                }
                Path out = extractRoot.resolve(entry.getName()).normalize();
                if (!out.startsWith(extractRoot)) {
                    throw new IOException("zip path escape: " + entry.getName());
                }
                Path outParent = out.getParent();
                if (outParent != null) {
                    Files.createDirectories(outParent);
                }
                Files.write(out, zip.readAllBytes());
            }
        }
    }

    private byte[] downloadItemBytes(CatalogIndex index, CatalogItem item) throws IOException {
        String base = index.baseUrl();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String itemPath = item.path().replace('\\', '/');
        while (itemPath.startsWith("/")) {
            itemPath = itemPath.substring(1);
        }
        URI uri = URI.create(base + itemPath);
        Request request = new Request.Builder().url(uri.toString()).get().build();
        try (Response response = http.newCall(request).execute()) {
            okhttp3.ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code() + " for " + uri);
            }
            return body.bytes();
        }
    }

    private CatalogIndex fetchManifest(String manifestUrl) throws IOException {
        Request request = new Request.Builder().url(manifestUrl).get().build();
        try (Response response = http.newCall(request).execute()) {
            okhttp3.ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("manifest HTTP " + response.code());
            }
            return CatalogIndex.parse(body.string());
        }
    }

    private void cacheRemoteManifest(CatalogIndex index, String manifestUrl) throws IOException {
        Path dir = agentRoot.resolve("sync/remote");
        Files.createDirectories(dir);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("fetchedAt", Instant.now().toString());
        root.put("manifestUrl", manifestUrl);
        root.put("catalogId", index.catalogId());
        PathIo.writeString(dir.resolve("catalog-index.meta.json"), MAPPER.writeValueAsString(root));
    }

    private void writePendingJson(List<CatalogSyncDiff.PendingItem> pending) throws IOException {
        Path file = agentRoot.resolve("sync/pending.json");
        Files.createDirectories(agentRoot.resolve("sync"));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("updatedAt", Instant.now().toString());
        ArrayNode items = MAPPER.createArrayNode();
        for (CatalogSyncDiff.PendingItem entry : pending) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", entry.item().id());
            node.put("kind", entry.item().kind());
            node.put("reason", entry.reason().name().toLowerCase());
            node.put("digest", entry.item().digest());
            items.add(node);
        }
        root.set("items", items);
        PathIo.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private String requireManifestUrl() {
        String url = CatalogSyncConfig.resolveManifestUrl(agentRoot);
        if (url.isBlank()) {
            throw new IllegalStateException(
                "未配置 catalog manifest：设置 AGENT1_CATALOG_MANIFEST_URL 或在 agent.manifest.json 添加 catalog.manifestUrl"
            );
        }
        return url;
    }

    public record SyncCheckResult(
        String manifestUrl,
        String catalogId,
        List<CatalogSyncDiff.PendingItem> pending,
        Map<String, Integer> pendingByKind
    ) {
    }

    public record SyncApplyResult(
        String manifestUrl,
        List<String> appliedIds,
        List<String> errors,
        Map<String, SyncState.InstalledItem> installed
    ) {
    }
}
