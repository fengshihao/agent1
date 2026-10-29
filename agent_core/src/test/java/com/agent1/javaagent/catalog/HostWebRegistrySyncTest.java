package com.agent1.javaagent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.OfficialCdn;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostWebRegistrySyncTest {

    @TempDir
    Path temp;

    @Test
    void parseDocxPreviewUrls_publishedManifestShellOnly() throws Exception {
        String json = officialManifestJson();
        var urls = HostWebRegistrySync.parseDocxPreviewUrls(json);
        assertTrue(urls.shell().contains("/shell.html"));
        var md = HostWebRegistrySync.parseMdPreviewUrls(json);
        assertTrue(md.shell().contains("/molan-embed/"));
    }

    @Test
    void writeCache_createsRuntimeDirAndRoundTrips() throws Exception {
        String json = officialManifestJson();
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
                "shell": "https://evil.invalid/evil/shell.html"
              }
            }
            """;
        assertThrows(IOException.class, () -> HostWebRegistrySync.parseDocxPreviewUrls(json));
    }

    private static String officialManifestJson() {
        String host = OfficialCdn.host();
        return """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://%s/host-web/shell/docx-preview/1/shell.html?v=1"
              },
              "md_preview": {
                "shell": "https://%s/host-web/molan-embed/1/shell.html?v=1"
              }
            }
            """.formatted(host, host);
    }
}
