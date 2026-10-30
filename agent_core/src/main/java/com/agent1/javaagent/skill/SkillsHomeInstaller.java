package com.agent1.javaagent.skill;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 安装官方 Skill 到 {@link SkillsHomePaths}，维护 {@code skills-index.json}。 */
public final class SkillsHomeInstaller {

    /** 安装完成后写入，供 registry 校验是否需重装（避免每次 load_skill 重复下 zip）。 */
    static final String ARTIFACT_SHA256_FILE = ".artifact-sha256";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkillsHomeInstaller() {
    }

    public static void installFromDirectory(Path agentRoot, String name, String version, Path sourceDir)
        throws IOException {
        if (name == null || name.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("name and version required");
        }
        if (!Files.isRegularFile(sourceDir.resolve("SKILL.md"))) {
            throw new IOException(name + ": SKILL.md required in source dir");
        }
        Path home = SkillsHomePaths.home(agentRoot);
        Files.createDirectories(home);
        Path dest = SkillsHomePaths.skillsDir(agentRoot).resolve(name + "@" + version);
        if (Files.exists(dest)) {
            deleteRecursive(dest);
        }
        copyTree(sourceDir, dest);
        updateIndex(agentRoot, name, version);
    }

    public static void installFromZip(Path agentRoot, String name, String version, byte[] zipBytes)
        throws IOException {
        installFromZip(agentRoot, name, version, zipBytes, null);
    }

    public static void installFromZip(
        Path agentRoot, String name, String version, byte[] zipBytes, String artifactSha256
    ) throws IOException {
        Path temp = Files.createTempDirectory("skill-zip-");
        try {
            unzip(zipBytes, temp);
            installFromDirectory(agentRoot, name, version, temp);
            if (artifactSha256 != null && !artifactSha256.isBlank()) {
                Path dest = SkillsHomePaths.skillsDir(agentRoot).resolve(name + "@" + version);
                writeArtifactSha(dest, artifactSha256);
            }
        } finally {
            deleteRecursive(temp);
        }
    }

    /** zip 内每个文件 entry 在 installDir 下均存在且为普通文件。 */
    static boolean installMatchesZip(Path installDir, byte[] zipBytes) throws IOException {
        return missingZipFileEntries(installDir, zipBytes).isEmpty();
    }

    static List<String> missingZipFileEntries(Path installDir, byte[] zipBytes) throws IOException {
        List<String> missing = new ArrayList<>();
        if (installDir == null || !Files.isDirectory(installDir)) {
            return List.of("(install dir missing)");
        }
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = normalizeZipEntryName(entry.getName());
                    if (entryName == null) {
                        continue;
                    }
                    Path out = resolveZipEntry(installDir, entryName);
                    if (!Files.isRegularFile(out)) {
                        missing.add(entryName);
                    }
                } finally {
                    zip.closeEntry();
                }
            }
        }
        return missing;
    }

    static boolean hasMatchingArtifactSha(Path installDir, String expectedSha256) {
        if (installDir == null || expectedSha256 == null || expectedSha256.isBlank()) {
            return false;
        }
        Path marker = installDir.resolve(ARTIFACT_SHA256_FILE);
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            String got = Files.readString(marker).trim().toLowerCase(Locale.ROOT);
            return got.equals(expectedSha256.trim().toLowerCase(Locale.ROOT));
        } catch (IOException e) {
            return false;
        }
    }

    static void writeArtifactSha(Path installDir, String sha256) throws IOException {
        PathIo.writeString(
            installDir.resolve(ARTIFACT_SHA256_FILE),
            sha256.trim().toLowerCase(Locale.ROOT) + "\n"
        );
    }

    static Map<String, String> readIndex(Path agentRoot) {
        Path file = SkillsHomePaths.indexFile(agentRoot);
        Map<String, String> index = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return index;
        }
        try {
            JsonNode root = MAPPER.readTree(PathIo.readString(file));
            root.fields().forEachRemaining(entry -> {
                String version = entry.getValue().asText("").trim();
                if (!version.isEmpty()) {
                    index.put(entry.getKey(), version);
                }
            });
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
        return index;
    }

    public static Path installedDir(Path agentRoot, String name) {
        String version = readIndex(agentRoot).get(name);
        if (version == null || version.isBlank()) {
            return null;
        }
        Path dir = SkillsHomePaths.skillsDir(agentRoot).resolve(name + "@" + version);
        return Files.isDirectory(dir) ? dir : null;
    }

    private static void updateIndex(Path agentRoot, String name, String version) throws IOException {
        Path file = SkillsHomePaths.indexFile(agentRoot);
        ObjectNode root;
        if (Files.isRegularFile(file)) {
            root = (ObjectNode) MAPPER.readTree(PathIo.readString(file));
        } else {
            root = MAPPER.createObjectNode();
        }
        root.put(name, version);
        PathIo.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private static void copyTree(Path source, Path dest) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                Path target = dest.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void unzip(byte[] zipBytes, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                try {
                    String entryName = normalizeZipEntryName(entry.getName());
                    if (entryName == null) {
                        continue;
                    }
                    Path out = resolveZipEntry(destDir, entryName);
                    Path normalizedRoot = destDir.toAbsolutePath().normalize();
                    if (!out.toAbsolutePath().normalize().startsWith(normalizedRoot)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Path parent = out.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    zip.closeEntry();
                }
            }
        }
    }

    private static String normalizeZipEntryName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.startsWith("/") || name.contains("..")) {
            return null;
        }
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        return name.isEmpty() ? null : name;
    }

    private static Path resolveZipEntry(Path destDir, String entryName) throws IOException {
        Path resolved = destDir;
        for (String part : entryName.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IOException("zip path escape: " + entryName);
            }
            resolved = resolved.resolve(part);
        }
        return resolved;
    }

    static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
