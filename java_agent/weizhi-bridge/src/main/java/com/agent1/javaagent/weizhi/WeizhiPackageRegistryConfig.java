package com.agent1.javaagent.weizhi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent1.javaagent.util.PathIo;
import com.weizhi.packages.PackageRegistryClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Registry URL 与超时：env 优先，其次 {@code agent.manifest.json}。 */
public final class WeizhiPackageRegistryConfig {

    public static final String ENV_REGISTRY_URL = "AGENT1_WEIZHI_PACKAGE_REGISTRY_URL";
    public static final String ENV_REGISTRY_FETCH_TIMEOUT_MS = "AGENT1_WEIZHI_REGISTRY_FETCH_TIMEOUT_MS";
    public static final String ENV_PACKAGE_ENSURE_TIMEOUT_MS = "AGENT1_WEIZHI_PACKAGE_ENSURE_TIMEOUT_MS";
    /** 逗号分隔 host；测试 / 镜像用。未设则仅 {@link PackageRegistryClient#OFFICIAL_HOST}。 */
    public static final String ENV_ALLOWED_HOSTS = "AGENT1_WEIZHI_PACKAGE_REGISTRY_ALLOWED_HOSTS";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WeizhiPackageRegistryConfig() {
    }

    /**
     * @return {@code null} 表示禁用远程 Registry（仅内置包）；非空为 manifest URL
     */
    public static String resolveRegistryUrl(Path agentRoot) {
        String fromEnv = System.getenv(ENV_REGISTRY_URL);
        if (fromEnv != null) {
            String trimmed = fromEnv.trim();
            if (trimmed.isEmpty() || isDisabled(trimmed)) {
                return null;
            }
            return trimmed;
        }
        if (agentRoot != null) {
            Path manifest = agentRoot.resolve("agent.manifest.json");
            if (Files.isRegularFile(manifest)) {
                try {
                    JsonNode root = MAPPER.readTree(PathIo.readString(manifest));
                    String url = root.path("weizhiPackage").path("registryUrl").asText("");
                    if (url != null && !url.isBlank()) {
                        String trimmed = url.trim();
                        return isDisabled(trimmed) ? null : trimmed;
                    }
                } catch (IOException ignored) {
                    // fall through
                }
            }
        }
        return PackageRegistryClient.DEFAULT_REGISTRY_URL;
    }

    public static long registryFetchTimeoutMs() {
        return positiveLong(
            System.getenv(ENV_REGISTRY_FETCH_TIMEOUT_MS),
            PackageRegistryClient.DEFAULT_MANIFEST_TIMEOUT_MS
        );
    }

    public static long packageEnsureTimeoutMs() {
        return positiveLong(
            System.getenv(ENV_PACKAGE_ENSURE_TIMEOUT_MS),
            PackageRegistryClient.DEFAULT_ENSURE_TIMEOUT_MS
        );
    }

    /**
     * @return {@code null} 表示使用 Weizhi 默认官方 host 白名单
     */
    public static List<String> resolveAllowedHosts(Path agentRoot) {
        String fromEnv = System.getenv(ENV_ALLOWED_HOSTS);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return splitHosts(fromEnv);
        }
        if (agentRoot != null) {
            Path manifest = agentRoot.resolve("agent.manifest.json");
            if (Files.isRegularFile(manifest)) {
                try {
                    JsonNode root = MAPPER.readTree(PathIo.readString(manifest));
                    JsonNode hosts = root.path("weizhiPackage").path("allowedHosts");
                    if (hosts.isArray() && !hosts.isEmpty()) {
                        List<String> out = new ArrayList<>();
                        for (JsonNode node : hosts) {
                            String h = node.asText("").trim();
                            if (!h.isEmpty()) {
                                out.add(h);
                            }
                        }
                        if (!out.isEmpty()) {
                            return out;
                        }
                    }
                } catch (IOException ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    private static List<String> splitHosts(String raw) {
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static boolean isDisabled(String value) {
        String v = value.toLowerCase();
        return "off".equals(v) || "none".equals(v) || "false".equals(v) || "0".equals(v);
    }

    private static long positiveLong(String raw, long defaultMs) {
        if (raw == null || raw.isBlank()) {
            return defaultMs;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : defaultMs;
        } catch (NumberFormatException e) {
            return defaultMs;
        }
    }
}
