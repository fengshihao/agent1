package com.agent1.javaagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link SkillsHomeInstaller} 云装 zip 解压（含 scripts/、references/ 子目录）。 */
class SkillsHomeInstallerZipTest {

    @TempDir
    Path agentRoot;

    @Test
    void installFromZipExtractsNestedPathsWithDirectoryEntries() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        byte[] zip = zipWithDirectoryEntriesThenFiles();

        SkillsHomeInstaller.installFromZip(agentRoot, "nested-skill", "1.0.0", zip, "abc");

        Path dir = SkillsHomeInstaller.installedDir(agentRoot, "nested-skill");
        Path script = dir.resolve("scripts/roadbook.js");
        Path reference = dir.resolve("references/sample.html");
        assertTrue(Files.isRegularFile(script), "scripts/roadbook.js");
        assertTrue(Files.isRegularFile(reference), "references/sample.html");
        assertEquals("js", Files.readString(script));
        assertEquals("<html></html>", Files.readString(reference));
        assertTrue(SkillsHomeInstaller.hasMatchingArtifactSha(dir, "abc"));
    }

    @Test
    void installMatchesZipDetectsMissingNestedFiles() throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        byte[] zip = zipWithDirectoryEntriesThenFiles();
        Path partial = agentRoot.resolve("partial");
        Files.createDirectories(partial);
        Files.writeString(partial.resolve("SKILL.md"), "x");

        assertTrue(!SkillsHomeInstaller.installMatchesZip(partial, zip));
        assertTrue(SkillsHomeInstaller.missingZipFileEntries(partial, zip).contains("scripts/roadbook.js"));
    }

    private static byte[] zipWithDirectoryEntriesThenFiles() throws Exception {
        Path temp = Files.createTempDirectory("skill-nested-zip-");
        Path zipPath = temp.resolve("pkg.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            putDirectoryEntry(zos, "scripts/");
            putFileEntry(zos, "scripts/roadbook.js", "js".getBytes(StandardCharsets.UTF_8));
            putDirectoryEntry(zos, "references/");
            putFileEntry(zos, "references/sample.html", "<html></html>".getBytes(StandardCharsets.UTF_8));
            putFileEntry(
                zos,
                "SKILL.md",
                "---\nname: nested\n---\n".getBytes(StandardCharsets.UTF_8)
            );
            putFileEntry(
                zos,
                "manifest.json",
                "{\"name\":\"nested-skill\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)
            );
        }
        return Files.readAllBytes(zipPath);
    }

    private static void putDirectoryEntry(ZipOutputStream zos, String path) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    private static void putFileEntry(ZipOutputStream zos, String path, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }
}
