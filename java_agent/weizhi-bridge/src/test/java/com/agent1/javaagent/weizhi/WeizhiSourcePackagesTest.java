package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weizhi.packages.PackageInstaller;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 纯 JVM：从仓内真源安装云端-only 包，不依赖 libweizhijni。 */
class WeizhiSourcePackagesTest {

    @Test
    void installsXlsxFromRepoSource(@TempDir Path home) throws Exception {
        Files.createDirectories(home);
        String version = WeizhiSourcePackages.installFromSource(home, "xlsx");
        assertEquals("1.1.0", version);
        assertEquals("1.1.0", PackageInstaller.readIndex(home).get("xlsx"));
        assertTrue(Files.isRegularFile(home.resolve("packages/xlsx@1.1.0/xlsx.js")));
        assertTrue(Files.readString(home.resolve("packages/xlsx@1.1.0/xlsx.js")).contains("setFormula"));
        assertTrue(Files.readString(home.resolve("packages/xlsx@1.1.0/xlsx.js")).contains("addChart"));
    }

    @Test
    void resolvePackageSourcePointsAtWeizhiPackages() throws Exception {
        Path dir = WeizhiSourcePackages.resolvePackageSource("xlsx-build");
        assertTrue(Files.isRegularFile(dir.resolve("xlsx-build.js")));
        assertTrue(dir.toString().contains("weizhi-packages"));
    }
}
