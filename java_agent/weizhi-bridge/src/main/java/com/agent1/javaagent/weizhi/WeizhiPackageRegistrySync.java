package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.capability.CapabilityRecord;
import com.weizhi.packages.PackageRegistryClient;
import com.weizhi.packages.PackageRegistryClient.FetchOptions;
import com.weizhi.packages.PackageRegistryClient.RegistryManifest;
import com.weizhi.packages.PackageRegistryClient.RegistryPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 将官方 Registry manifest 合并进 find_caps（kind=weizhi_package）。 */
public final class WeizhiPackageRegistrySync {

    private static final Logger LOG = Logger.getLogger(WeizhiPackageRegistrySync.class.getName());

    private WeizhiPackageRegistrySync() {
    }

    public static SyncResult ensureIndexed(Path agentRoot) {
        if (agentRoot == null) {
            return SyncResult.skipped("no agentRoot");
        }
        String registryUrl = WeizhiPackageRegistryConfig.resolveRegistryUrl(agentRoot);
        if (registryUrl == null) {
            return SyncResult.skipped("registry disabled");
        }
        Path cacheFile = agentRoot.resolve("sync/weizhi-package-registry-cache.json");
        try {
            Files.createDirectories(cacheFile.getParent());
        } catch (IOException e) {
            return SyncResult.failed(e.getMessage());
        }
        FetchOptions options = new FetchOptions();
        options.timeoutMs = WeizhiPackageRegistryConfig.registryFetchTimeoutMs();
        options.cacheFile = cacheFile;
        options.allowedHosts = WeizhiPackageRegistryConfig.resolveAllowedHosts(agentRoot);
        try {
            RegistryManifest manifest = PackageRegistryClient.fetchManifest(registryUrl, options);
            List<CapabilityRecord> records = toRecords(manifest);
            CapabilityIndexStore.replaceKind(agentRoot, "weizhi_package", records);
            return new SyncResult(true, records.size(), null);
        } catch (IOException e) {
            LOG.log(Level.FINE, "weizhi package registry index skipped", e);
            return SyncResult.failed(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "weizhi package registry index failed", e);
            return SyncResult.failed(e.getMessage());
        }
    }

    /**
     * 后台：对已安装 name 尝试对照 Registry 升级（upgrade=true）。
     */
    public static void backgroundUpgradeInstalled(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        backgroundUpgradeInstalled(agentRoot, WeizhiBundledPackages.weizhiHome(agentRoot));
    }

    public static void backgroundUpgradeInstalled(Path agentRoot, Path weizhiHome) {
        if (agentRoot == null || weizhiHome == null) {
            return;
        }
        String registryUrl = WeizhiPackageRegistryConfig.resolveRegistryUrl(agentRoot);
        if (registryUrl == null) {
            return;
        }
        List<String> installed = readInstalledNames(weizhiHome);
        if (installed.isEmpty()) {
            return;
        }
        PackageRegistryClient.EnsureOptions options = new PackageRegistryClient.EnsureOptions();
        options.registryUrl = registryUrl;
        options.timeoutMs = WeizhiPackageRegistryConfig.packageEnsureTimeoutMs();
        options.upgrade = true;
        options.cacheFile = weizhiHome.resolve("registry-cache.json");
        options.allowedHosts = WeizhiPackageRegistryConfig.resolveAllowedHosts(agentRoot);
        try {
            PackageRegistryClient.ensureInstalled(weizhiHome, installed, options);
        } catch (IOException e) {
            LOG.log(Level.FINE, "weizhi package background upgrade skipped", e);
        }
    }

    private static List<String> readInstalledNames(Path weizhiHome) {
        Path index = weizhiHome.resolve("packages-index.json");
        if (!Files.isRegularFile(index)) {
            return List.of();
        }
        try {
            String json = Files.readString(index);
            if (json.isBlank()) {
                return List.of();
            }
            java.util.Map<String, Object> map = com.weizhi.platform.MiniJson.object(json);
            if (map == null || map.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(map.keySet());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<CapabilityRecord> toRecords(RegistryManifest manifest) {
        List<CapabilityRecord> out = new ArrayList<>();
        if (manifest == null || manifest.packages == null) {
            return out;
        }
        for (RegistryPackage pkg : manifest.packages) {
            if (pkg == null || pkg.name == null || pkg.name.isBlank()) {
                continue;
            }
            String title = pkg.capabilityTitle == null || pkg.capabilityTitle.isBlank()
                ? pkg.name
                : pkg.capabilityTitle;
            String summary = pkg.description == null ? "" : pkg.description;
            if (pkg.capabilitySummary != null && !pkg.capabilitySummary.isBlank()) {
                summary = pkg.capabilitySummary;
            }
            String entry = pkg.capabilityEntry == null || pkg.capabilityEntry.isBlank()
                ? "import … from \"weizhi:" + pkg.name + "\";"
                : pkg.capabilityEntry;
            String tags = "weizhi package " + pkg.name + " " + pkg.version;
            out.add(
                new CapabilityRecord(
                    "weizhi_pkg." + pkg.name,
                    "weizhi_package",
                    title,
                    summary,
                    tags,
                    "any",
                    entry,
                    "",
                    "",
                    "",
                    "registry:" + manifest.channel,
                    1.0
                )
            );
        }
        return out;
    }

    public record SyncResult(boolean indexed, int packageCount, String warning) {
        public static SyncResult skipped(String reason) {
            return new SyncResult(false, 0, reason);
        }

        public static SyncResult failed(String message) {
            return new SyncResult(false, 0, message);
        }
    }
}
