package com.agent1.javaagent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostWebRegistrySyncTest {

    @TempDir
    Path temp;

    @Test
    void parseDocxPreviewUrls_publishedManifestShellOnly() throws Exception {
        String json = """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/shell.html?v=1"
              },
              "md_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/molan-embed/1/shell.html?v=1"
              }
            }
            """;
        var urls = HostWebRegistrySync.parseDocxPreviewUrls(json);
        assertTrue(urls.shell().contains("/shell.html"));
        var md = HostWebRegistrySync.parseMdPreviewUrls(json);
        assertTrue(md.shell().contains("/molan-embed/"));
    }

    @Test
    void writeCache_createsRuntimeDirAndRoundTrips() throws Exception {
        String json = """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/shell.html?v=1"
              },
              "md_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/molan-embed/1/shell.html?v=1"
              }
            }
            """;
        HostWebRegistrySync.writeCache(temp, json);
        assertEquals(json, HostWebRegistrySync.readCache(temp));
        assertTrue(HostWebRegistrySync.docxPreviewUrls(temp).shell().contains("/shell.html"));
    }

    @Test
    void parseDocxPreviewUrls_rejectsNonOfficialHost() {
        String json = """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://cdn.example.com/evil/shell.html"
              }
            }
            """;
        assertThrows(IOException.class, () -> HostWebRegistrySync.parseDocxPreviewUrls(json));
    }
}
