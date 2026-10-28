package com.agent1.javaagent.weizhi;

import com.weizhi.packages.PackageInstaller;
import java.nio.file.Path;

/**
 * weizhi-home 路径约定。生产路径无 classpath 预装：package 靠云端 registry
 * 惰性安装（{@code WeizhiPackageSync.beforeEval}）；集成测试用固化 fixture
 * （{@code WeizhiTestPackages}，test-only）。
 * <p>
 * 历史上的桌面 bundled classpath 安装已移除（能上云的内容不进 jar/APK）。
 */
public final class WeizhiBundledPackages {

    private WeizhiBundledPackages() {
    }

    public static Path weizhiHome(Path agentRoot) {
        if (agentRoot == null) {
            throw new IllegalArgumentException("agentRoot");
        }
        return PackageInstaller.weizhiHome(agentRoot.toAbsolutePath().normalize());
    }
}
