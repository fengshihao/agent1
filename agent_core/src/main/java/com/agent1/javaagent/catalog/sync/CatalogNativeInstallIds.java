package com.agent1.javaagent.catalog.sync;

import com.agent1.javaagent.catalog.CatalogIndex;
import com.agent1.javaagent.catalog.CatalogItem;
import com.agent1.javaagent.catalog.CatalogPlatform;
import java.util.ArrayList;
import java.util.List;

/** 由 Weizhi 插件名（{@code ensureNative("echo_math")}）解析 manifest 中 native 条目 id。 */
public final class CatalogNativeInstallIds {

    private CatalogNativeInstallIds() {
    }

    public static List<String> forPlugin(CatalogIndex index, String pluginName) {
        if (index == null || pluginName == null || pluginName.isBlank()) {
            return List.of();
        }
        String name = pluginName.trim();
        String platform = CatalogPlatform.currentLabel();
        String pathNeedle = "/" + name + "/";
        List<String> ids = new ArrayList<>();
        for (CatalogItem item : index.items()) {
            if (!"native".equalsIgnoreCase(item.kind())) {
                continue;
            }
            if (item.platform().isPresent() && !platform.equalsIgnoreCase(item.platform().get())) {
                continue;
            }
            String path = item.path().replace('\\', '/');
            if (path.contains(pathNeedle) || idMatchesPlugin(item.id(), name)) {
                ids.add(item.id());
            }
        }
        return List.copyOf(ids);
    }

    private static boolean idMatchesPlugin(String id, String pluginName) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return id.contains("." + pluginName + ".") || id.endsWith("." + pluginName);
    }
}
