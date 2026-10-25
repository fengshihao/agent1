package com.agent1.javaagent.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;

public class HostWebRegistrySyncTest {

    @Test
    public void parseDocxPreviewUrls_includesShell() throws IOException {
        String json = """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/shell.html",
                "jszip": "https://agentone.guoyoutech.cn/host-web/vendor/jszip/3.10.1/jszip.min.js",
                "docx_preview": "https://agentone.guoyoutech.cn/host-web/vendor/docx-preview/0.4.1/docx-preview.min.js"
              }
            }
            """;
        var urls = HostWebRegistrySync.parseDocxPreviewUrls(json);
        assertTrue(urls.shell().contains("/shell.html"));
        assertTrue(urls.docxPreview().contains("docx-preview"));
    }

    @Test
    public void parseDocxPreviewUrls_rejectsNonOfficialHost() {
        String json = """
            {
              "schema_version": 1,
              "docx_preview": {
                "shell": "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/shell.html",
                "jszip": "https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js",
                "docx_preview": "https://agentone.guoyoutech.cn/host-web/vendor/docx-preview/0.4.1/docx-preview.min.js"
              }
            }
            """;
        assertThrows(IOException.class, () -> HostWebRegistrySync.parseDocxPreviewUrls(json));
    }
}
