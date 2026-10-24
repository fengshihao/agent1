package com.agent1.javaagent.skill;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 安装官方 Skill 到 {@link SkillsHomePaths}，维护 {@code skills-index.json}。 */
public final class SkillsHomeInstaller {

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
        Path temp = Files.createTempDirectory("skill-zip-");
        try {
            unzip(zipBytes, temp);
            installFromDirectory(agentRoot, name, version, temp);
        } finally {
            deleteRecursive(temp);
        }
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
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(destDir.resolve(entry.getName()));
                    continue;
                }
                String name = entry.getName();
                if (name.contains("..")) {
                    continue;
                }
                Path out = destDir.resolve(name);
                Path parent = out.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
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
