package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.weizhi.packages.PackageInstaller;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeizhiPackageSyncTest {

    @TempDir
    Path temp;

    @Test
    void packageNamesStopsAtNameBoundary() {
        String source = """
            import { markdownToDocx } from "weizhi:docx";
            import { buildDocx } from 'weizhi:docx-build';
            import { validateDocx } from "weizhi:docx-raw";
            """;
        assertEquals(List.of("docx", "docx-build", "docx-raw"), WeizhiPackageSync.packageNames(source));
    }

    @Test
    void skipsEnsureWhenBundledPackageInstalledButNotInRegistry(@TempDir Path home) throws Exception {
        Path dir = Files.createTempDirectory("video-pkg");
        Files.writeString(dir.resolve("manifest.json"),
            "{\"name\":\"video\",\"version\":\"0.6.0\",\"entry\":\"index.js\"}");
        Files.writeString(dir.resolve("index.js"), "export const probe = () => ({});\n");
        PackageInstaller.installPackageDirectory(home, "video", "0.6.0", dir);
        String manifest = "{\"packages\":[{\"name\":\"docx\",\"version\":\"1.0.0\"}]}";
        assertEquals(List.of(), WeizhiPackageSync.namesNeedingEnsure(home, List.of("video"), manifest));
        assertEquals(List.of("docx"), WeizhiPackageSync.namesNeedingEnsure(home, List.of("docx"), manifest));
        Path emptyHome = Files.createTempDirectory("weizhi-home-empty");
        assertEquals(List.of("video"), WeizhiPackageSync.namesNeedingEnsure(emptyHome, List.of("video"), manifest));
    }

    @Test
    void manifestCardsReplaceRegistryRowsKeepSeed() throws Exception {
        Path agentRoot = temp.resolve("agent");
        CapabilityIndexStore.ensure(agentRoot);
        String json = """
            {"schema_version":1,"packages":[{
              "name":"docx","version":"1.0.0",
              "cards":[{
                "id":"docx.markdownToDocx",
                "title":"云端 Word",
                "summary":"清单里的当前卡片",
                "tags":["docx","word"],
                "platforms":["any"],
                "entry":"import { markdownToDocx } from \\"weizhi:docx\\";"
              }]
            }]}
            """;
        WeizhiPackageSync.applyManifest(agentRoot, json);

        var hits = CapabilityIndexStore.search(agentRoot, "清单里的当前卡片", List.of(), "any", 5);
        assertTrue(hits.stream().anyMatch(hit -> "docx.markdownToDocx".equals(hit.id())));
        assertTrue(hits.stream().anyMatch(hit -> "云端 Word".equals(hit.title())));

        WeizhiPackageSync.applyManifest(agentRoot, "{\"packages\":[]}");
        var after = CapabilityIndexStore.search(agentRoot, "清单里的当前卡片", List.of(), "any", 5);
        assertTrue(after.stream().noneMatch(hit -> "云端 Word".equals(hit.title())));
        var remaining = CapabilityIndexStore.search(agentRoot, "skill creator", List.of(), "any", 5);
        assertTrue(remaining.stream().anyMatch(hit -> "skill.skill-creator".equals(hit.id())));
        var video = CapabilityIndexStore.search(agentRoot, "exportVideo", List.of(), "android", 5);
        assertTrue(video.stream().anyMatch(hit -> "media.video".equals(hit.id())));
    }
}
