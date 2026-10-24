package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.util.PathIo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import okio.Buffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillRegistryClientTest {

    @TempDir
    Path agentRoot;

    @Test
    void ensureInstalledFromRegistryManifest() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        byte[] zip = zipSkill("demo-skill", "1.0.0", "demo body");
        String sha = sha256Hex(zip);

        try (MockWebServer server = new MockWebServer()) {
            String manifest =
                """
                {
                  "schema_version": 1,
                  "channel": "stable",
                  "artifacts": [
                    {
                      "kind": "skill",
                      "name": "demo-skill",
                      "version": "1.0.0",
                      "artifact": {
                        "url": "%s",
                        "sha256": "%s",
                        "size": %d
                      }
                    }
                  ]
                }
                """
                    .formatted(server.url("/skill.zip"), sha, zip.length);
            server.enqueue(new MockResponse().setBody(manifest));
            Buffer zipBody = new Buffer();
            zipBody.write(zip);
            server.enqueue(new MockResponse().setBody(zipBody));

            System.setProperty(SkillRegistryClient.PROP_REGISTRY_URL, server.url("/registry/stable.json").toString());
            try {
                SkillRegistryClient.EnsureResult result =
                    SkillRegistryClient.ensureInstalled(agentRoot, List.of("demo-skill"));
                assertEquals(List.of("demo-skill"), result.installed());
                Path skillMd = SkillsHomeInstaller.installedDir(agentRoot, "demo-skill").resolve("SKILL.md");
                assertTrue(Files.readString(skillMd).contains("demo body"));
            } finally {
                System.clearProperty(SkillRegistryClient.PROP_REGISTRY_URL);
            }
        }
    }

    private static byte[] zipSkill(String name, String version, String body) throws Exception {
        Path temp = Files.createTempDirectory("skill-zip-test");
        Path dir = temp.resolve("pkg");
        Files.createDirectories(dir);
        PathIo.writeString(
            dir.resolve("manifest.json"),
            "{\"name\":\"" + name + "\",\"version\":\"" + version + "\"}"
        );
        PathIo.writeString(
            dir.resolve("SKILL.md"),
            "---\nname: " + name + "\ndescription: test\n---\n" + body + "\n"
        );
        Path zipPath = temp.resolve("out.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Path file : List.of(dir.resolve("manifest.json"), dir.resolve("SKILL.md"))) {
                ZipEntry entry = new ZipEntry(file.getFileName().toString());
                zos.putNextEntry(entry);
                zos.write(Files.readAllBytes(file));
                zos.closeEntry();
            }
        }
        return Files.readAllBytes(zipPath);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest(bytes)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
