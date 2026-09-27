package com.agent1.javaagent.catalog.sync;

import com.agent1.javaagent.catalog.CatalogItem;
import com.agent1.javaagent.catalog.CatalogIndex;
import com.agent1.javaagent.catalog.CatalogPlatform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogSyncDiff {

    public enum PendingReason {
        NEW,
        UPDATED
    }

    public record PendingItem(CatalogItem item, PendingReason reason) {
    }

    private CatalogSyncDiff() {
    }

    public static List<PendingItem> pending(CatalogIndex index, SyncState state) {
        String platform = CatalogPlatform.currentLabel();
        List<CatalogItem> candidates = index.itemsForPlatform(platform);
        List<PendingItem> pending = new ArrayList<>();
        for (CatalogItem item : candidates) {
            SyncState.InstalledItem installed = state.installed(item.id()).orElse(null);
            if (installed == null) {
                pending.add(new PendingItem(item, PendingReason.NEW));
                continue;
            }
            if (!installed.digest().equalsIgnoreCase(item.digest())) {
                pending.add(new PendingItem(item, PendingReason.UPDATED));
            }
        }
        return pending;
    }

    public static Map<String, Integer> pendingCountByKind(List<PendingItem> pending) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PendingItem entry : pending) {
            String kind = entry.item().kind();
            counts.merge(kind, 1, Integer::sum);
        }
        return counts;
    }
}
