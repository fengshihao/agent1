package com.agent1.javaagent.weizhi;

import java.nio.file.Path;

/** Productivity Host 装配：find_caps 索引刷新 + 后台升级。 */
public final class WeizhiPackageRegistryHooks {

    private WeizhiPackageRegistryHooks() {
    }

    public static Runnable refreshTask(Path agentRoot) {
        if (agentRoot == null) {
            return null;
        }
        if (WeizhiPackageRegistryConfig.resolveRegistryUrl(agentRoot) == null) {
            return null;
        }
        return () -> {
            WeizhiPackageRegistrySync.ensureIndexed(agentRoot);
            WeizhiPackageRegistrySync.backgroundUpgradeInstalled(agentRoot);
        };
    }
}
