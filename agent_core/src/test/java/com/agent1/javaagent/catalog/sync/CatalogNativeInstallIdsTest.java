package com.agent1.javaagent.catalog.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.catalog.CatalogIndex;
import com.agent1.javaagent.catalog.CatalogItem;
import com.agent1.javaagent.catalog.CatalogPlatform;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogNativeInstallIdsTest {

    @Test
    void resolvesEchoMathIdsForCurrentPlatform() {
        String platform = CatalogPlatform.currentLabel();
        CatalogIndex index = new CatalogIndex(
            1,
            "t",
            "https://example/",
            List.of(
                new CatalogItem(
                    "native.echo_math.manifest",
                    "native",
                    "1",
                    "sha256:ab",
                    "native/" + platform + "/echo_math/manifest.json",
                    platform,
                    0
                ),
                new CatalogItem(
                    "native.echo_math.so",
                    "native",
                    "1",
                    "sha256:cd",
                    "native/" + platform + "/echo_math/libecho_math.so",
                    platform,
                    0
                ),
                new CatalogItem(
                    "native.echo_math.other",
                    "native",
                    "1",
                    "sha256:ef",
                    "native/mac/echo_math/x",
                    "mac",
                    0
                )
            )
        );
        List<String> ids = CatalogNativeInstallIds.forPlugin(index, "echo_math");
        assertEquals(2, ids.size());
        assertTrue(ids.contains("native.echo_math.manifest"));
        assertTrue(ids.contains("native.echo_math.so"));
    }
}
