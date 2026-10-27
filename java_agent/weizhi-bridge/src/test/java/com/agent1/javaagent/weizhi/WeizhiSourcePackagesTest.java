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
        assertEquals("1.2.0", version);
        assertEquals("1.2.0", PackageInstaller.readIndex(home).get("xlsx"));
        assertTrue(Files.isRegularFile(home.resolve("packages/xlsx@1.2.0/xlsx.js")));
        assertTrue(Files.readString(home.resolve("packages/xlsx@1.2.0/xlsx.js")).contains("setFormula"));
        assertTrue(Files.readString(home.resolve("packages/xlsx@1.2.0/xlsx.js")).contains("addChart"));
        assertTrue(Files.readString(home.resolve("packages/xlsx@1.2.0/xlsx.js")).contains("patchSave"));
    }

    @Test
    void installsXlsxRawFromRepoSource(@TempDir Path home) throws Exception {
        Files.createDirectories(home);
        String version = WeizhiSourcePackages.installFromSource(home, "xlsx-raw");
        assertEquals("1.0.0", version);
        assertTrue(Files.isRegularFile(home.resolve("packages/xlsx-raw@1.0.0/xlsx-raw.js")));
        assertTrue(Files.readString(home.resolve("packages/xlsx-raw@1.0.0/xlsx-raw.js")).contains("validateXlsx"));
    }

    @Test
    void cloudOnlyListCoversXlsxFamily() {
        assertTrue(WeizhiSourcePackages.isCloudOnly("xlsx"));
        assertTrue(WeizhiSourcePackages.isCloudOnly("xlsx-build"));
        assertTrue(WeizhiSourcePackages.isCloudOnly("xlsx-raw"));
    }

    @Test
    void resolvePackageSourcePointsAtWeizhiPackages() throws Exception {
        Path dir = WeizhiSourcePackages.resolvePackageSource("xlsx-build");
        assertTrue(Files.isRegularFile(dir.resolve("xlsx-build.js")));
        assertTrue(dir.toString().contains("weizhi-packages"));
    }
}
