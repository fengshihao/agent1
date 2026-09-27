package com.agent1.javaagent.catalog;

import java.util.Locale;

/** 与 manifest native.platform 对齐的粗粒度标签。 */
public final class CatalogPlatform {

    private CatalogPlatform() {
    }

    public static String currentLabel() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osToken;
        if (os.contains("mac") || os.contains("darwin")) {
            osToken = "macos";
        } else if (os.contains("win")) {
            osToken = "windows";
        } else {
            osToken = "linux";
        }
        String archToken;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            archToken = "x64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            archToken = "arm64";
        } else {
            archToken = arch.replaceAll("[^a-z0-9]", "");
        }
        return osToken + "-" + archToken;
    }
}
