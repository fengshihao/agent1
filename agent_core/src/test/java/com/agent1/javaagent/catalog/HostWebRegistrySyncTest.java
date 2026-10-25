package com.agent1.javaagent.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;

public class HostWebRegistrySyncTest {

    @Test
    public void parseDocxPreviewUrls_publishedManifestShellOnly() throws IOException {
        String json = """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/shell.html"
              }
            }
            """;
        var urls = HostWebRegistrySync.parseDocxPreviewUrls(json);
        assertTrue(urls.shell().contains("/shell.html"));
    }

    @Test
    public void parseDocxPreviewUrls_rejectsNonOfficialHost() {
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
