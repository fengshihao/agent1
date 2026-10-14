package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptWeizhiPackageEnsurer;
import com.weizhi.packages.PackageRegistryClient;
import com.weizhi.packages.PackageRegistryClient.EnsureOptions;
import com.weizhi.packages.PackageRegistryClient.EnsureResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** run_js 前：sync 内置包 + Registry ensure（独立超时）。 */
public final class WeizhiScriptPackageEnsurer implements ScriptWeizhiPackageEnsurer {

    private final Path agentRoot;
    private final Path weizhiHome;
    private final Runnable bundledSync;

    public WeizhiScriptPackageEnsurer(Path agentRoot) {
        this(agentRoot, agentRoot == null ? null : WeizhiBundledPackages.weizhiHome(agentRoot), null);
    }

    public WeizhiScriptPackageEnsurer(Path agentRoot, Path weizhiHome) {
        this(agentRoot, weizhiHome, null);
    }

    /**
     * @param bundledSync 非空时替代 classpath 内置包同步（Android {@code WeizhiPackages.syncBundled}）
     */
    public WeizhiScriptPackageEnsurer(Path agentRoot, Path weizhiHome, Runnable bundledSync) {
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
        this.weizhiHome = weizhiHome == null ? null : weizhiHome.toAbsolutePath().normalize();
        this.bundledSync = bundledSync;
    }

    public static ScriptWeizhiPackageEnsurer optional(Path agentRoot) {
        return optional(agentRoot, agentRoot == null ? null : WeizhiBundledPackages.weizhiHome(agentRoot));
    }

    public static ScriptWeizhiPackageEnsurer optional(Path agentRoot, Path weizhiHome) {
        return optional(agentRoot, weizhiHome, null);
    }

    public static ScriptWeizhiPackageEnsurer optional(Path agentRoot, Path weizhiHome, Runnable bundledSync) {
        if (agentRoot == null || weizhiHome == null) {
            return null;
        }
        if (WeizhiPackageRegistryConfig.resolveRegistryUrl(agentRoot) == null) {
            return null;
        }
        return new WeizhiScriptPackageEnsurer(agentRoot, weizhiHome, bundledSync);
    }

    @Override
    public String ensureForSource(String scriptSource, CancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return "错误：执行已取消";
        }
        Set<String> names = WeizhiPackageImports.scan(scriptSource);
        if (names.isEmpty()) {
            return null;
        }
        String registryUrl = WeizhiPackageRegistryConfig.resolveRegistryUrl(agentRoot);
        if (registryUrl == null) {
            return null;
        }
        try {
            if (bundledSync != null) {
                bundledSync.run();
            } else {
                WeizhiBundledPackages.syncFromClasspath(agentRoot);
            }
        } catch (IOException e) {
            return "错误：同步内置 Weizhi 包失败: " + e.getMessage();
        } catch (RuntimeException e) {
            return "错误：同步内置 Weizhi 包失败: " + e.getMessage();
        }
        if (weizhiHome == null) {
            return "错误：Weizhi 包目录未配置";
        }
        EnsureOptions options = new EnsureOptions();
        options.registryUrl = registryUrl;
        options.timeoutMs = WeizhiPackageRegistryConfig.packageEnsureTimeoutMs();
        options.cacheFile = weizhiHome.resolve("registry-cache.json");
        options.upgrade = true;
        options.allowedHosts = WeizhiPackageRegistryConfig.resolveAllowedHosts(agentRoot);
        try {
            EnsureResult result = PackageRegistryClient.ensureInstalled(weizhiHome, names, options);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return "错误：执行已取消";
            }
            if (!result.installed.isEmpty() || !result.unchanged.isEmpty()) {
                return null;
            }
            return null;
        } catch (IOException e) {
            return "错误：Weizhi 包安装失败: " + e.getMessage();
        }
    }
}
