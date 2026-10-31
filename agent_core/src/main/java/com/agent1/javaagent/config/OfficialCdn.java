package com.agent1.javaagent.config;

import java.net.URI;
import java.util.Locale;

/**
 * 官方制品 CDN（packages / skills / web-libs / host-web）的唯一主机配置。
 *
 * <p>源码不内置生产域名。解析顺序：{@code AGENT1_CDN_BASE_URL} →
 * {@code ARTIFACT_CDN_BASE_URL} → {@code AGENT1_CDN_HOST} → 系统属性
 * {@code agent1.cdn.baseUrl} / {@code agent1.cdn.host} → {@link #configure(String)}
 * （Android {@code BuildConfig}）→ 占位主机 {@link #UNCONFIGURED_HOST}。
 */
public final class OfficialCdn {

    public static final String ENV_BASE_URL = "AGENT1_CDN_BASE_URL";
    public static final String ENV_BASE_URL_ALIAS = "ARTIFACT_CDN_BASE_URL";
    public static final String ENV_HOST = "AGENT1_CDN_HOST";
    public static final String PROP_BASE_URL = "agent1.cdn.baseUrl";
    public static final String PROP_HOST = "agent1.cdn.host";

    /** 未配置时的占位主机，避免仓库出现生产域名；网络请求会失败直到配置。 */
    public static final String UNCONFIGURED_HOST = "cdn.example.test";

    public static final String PACKAGES_REGISTRY_PATH = "packages/registry/stable.json";
    public static final String GENERIC_REGISTRY_PATH = "registry/stable.json";
    public static final String SKILLS_REGISTRY_PATH = "skills/registry/stable.json";
    public static final String WEB_LIBS_REGISTRY_PATH = "web-libs/registry/stable.json";
    public static final String HOST_WEB_REGISTRY_PATH = "host-web/registry/stable.json";
    public static final String DOCX_PREVIEW_SHELL_PATH =
        "host-web/shell/docx-preview/1/shell.html?v=20261031b";
    public static final String MOLAN_EMBED_SHELL_PATH =
        "host-web/molan-embed/1/shell.html?v=20261027c";

    private static volatile String configured;

    private OfficialCdn() {
    }

    /**
     * 程序设置主机或 {@code https://host/}。空值清除。优先级低于环境变量与系统属性。
     */
    public static void configure(String hostOrBaseUrl) {
        if (hostOrBaseUrl == null || hostOrBaseUrl.isBlank()) {
            configured = null;
            return;
        }
        configured = parseHost(hostOrBaseUrl.trim());
    }

    public static String host() {
        return resolveHost(
            env(ENV_BASE_URL),
            env(ENV_BASE_URL_ALIAS),
            env(ENV_HOST),
            System.getProperty(PROP_BASE_URL, ""),
            System.getProperty(PROP_HOST, ""),
            configured);
    }

    /** 环境变量、系统属性或 {@link #configure(String)} 是否提供了主机。 */
    public static boolean isConfigured() {
        return firstNonBlank(
                env(ENV_BASE_URL),
                env(ENV_BASE_URL_ALIAS),
                env(ENV_HOST),
                System.getProperty(PROP_BASE_URL, ""),
                System.getProperty(PROP_HOST, ""),
                configured)
            != null;
    }

    public static String baseUrl() {
        return "https://" + host() + "/";
    }

    public static String https(String relativePathAndQuery) {
        String path = relativePathAndQuery == null ? "" : relativePathAndQuery.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return baseUrl() + path;
    }

    public static String packagesRegistryUrl() {
        return https(PACKAGES_REGISTRY_PATH);
    }

    public static String genericRegistryUrl() {
        return https(GENERIC_REGISTRY_PATH);
    }

    public static String skillsRegistryUrl() {
        return https(SKILLS_REGISTRY_PATH);
    }

    public static String webLibsRegistryUrl() {
        return https(WEB_LIBS_REGISTRY_PATH);
    }

    public static String hostWebRegistryUrl() {
        return https(HOST_WEB_REGISTRY_PATH);
    }

    public static String docxPreviewShellUrl() {
        return https(DOCX_PREVIEW_SHELL_PATH);
    }

    public static String molanEmbedShellUrl() {
        return https(MOLAN_EMBED_SHELL_PATH);
    }

    public static boolean isLoopback(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    /** 回环或当前官方 CDN 主机。 */
    public static boolean isAllowedHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return isLoopback(host) || host().equalsIgnoreCase(host);
    }

    static String resolveHost(
        String envBaseUrl,
        String envBaseUrlAlias,
        String envHost,
        String propBaseUrl,
        String propHost,
        String configuredHost) {
        String raw = firstNonBlank(
            envBaseUrl, envBaseUrlAlias, envHost, propBaseUrl, propHost, configuredHost);
        if (raw == null) {
            return UNCONFIGURED_HOST;
        }
        return parseHost(raw);
    }

    static String parseHost(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("cdn host is blank");
        }
        if (trimmed.contains("://")) {
            URI uri;
            try {
                uri = URI.create(trimmed);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("cdn base url is invalid", e);
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("cdn base url must be http or https");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("cdn base url must not contain credentials");
            }
            String parsedHost = uri.getHost();
            if (parsedHost == null || parsedHost.isBlank()) {
                throw new IllegalArgumentException("cdn base url missing host");
            }
            return parsedHost.toLowerCase(Locale.ROOT);
        }
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            trimmed = trimmed.substring(0, colon);
        }
        if (trimmed.isEmpty() || trimmed.contains("@")) {
            throw new IllegalArgumentException("cdn host is invalid");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
