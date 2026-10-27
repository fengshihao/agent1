package com.agent1.javaagent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 将 classpath {@code agent-home/skills-bundled/<name>/} 安装到 {@link SkillsHomePaths}。 */
public final class SkillsBundledInstall {

    public static final List<String> BUNDLED_SKILL_NAMES = List.of("html2pptx", "travel-itinerary", "pptx", "video-edit");

    private static final String RESOURCE_ROOT = "agent-home/skills-bundled/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object INSTALL_LOCK = new Object();

    private SkillsBundledInstall() {
    }

    public static void syncFromClasspath(Path agentRoot) throws IOException {
        synchronized (INSTALL_LOCK) {
            Files.createDirectories(SkillsHomePaths.home(agentRoot));
            for (String name : BUNDLED_SKILL_NAMES) {
                installBundled(agentRoot, name);
            }
        }
        SkillsCapabilitySync.syncCapabilityCards(agentRoot);
    }

    public static boolean isBundled(String name) {
        return name != null && BUNDLED_SKILL_NAMES.contains(name);
    }

    public static void ensureBundled(Path agentRoot, String name) throws IOException {
        if (!isBundled(name)) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            installBundled(agentRoot, name);
        }
        SkillsCapabilitySync.syncCapabilityCards(agentRoot);
    }

    public static String bundledVersion(String name) throws IOException {
        JsonNode node = readManifestResource(name);
        return node.path("version").asText("").trim();
    }

    private static void installBundled(Path agentRoot, String name) throws IOException {
        JsonNode manifest = readManifestResource(name);
        String version = manifest.path("version").asText("").trim();
        if (version.isEmpty()) {
            throw new IOException(name + ": manifest.version required");
        }
        String manifestName = manifest.path("name").asText("").trim();
        if (!name.equals(manifestName)) {
            throw new IOException(name + ": manifest.name mismatch");
        }
        Path temp = Files.createTempDirectory("skill-bundled-");
        try {
            copyResourceTree(name, temp);
            SkillsHomeInstaller.installFromDirectory(agentRoot, name, version, temp);
        } finally {
            SkillsHomeInstaller.deleteRecursive(temp);
        }
    }

    private static JsonNode readManifestResource(String name) throws IOException {
        String resourcePath = RESOURCE_ROOT + name + "/manifest.json";
        try (InputStream in = SkillsBundledInstall.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("missing resource: " + resourcePath);
            }
            return MAPPER.readTree(in);
        }
    }

    private static void copyResourceTree(String name, Path dest) throws IOException {
        String prefix = RESOURCE_ROOT + name + "/";
        copyResourceFile(prefix + "manifest.json", dest.resolve("manifest.json"));
        copyResourceFile(prefix + "SKILL.md", dest.resolve("SKILL.md"));
        copyOptionalScripts(prefix, dest);
    }

    /** 可选 scripts/ 子目录：file classpath 直接列举；jar 按 entry 列举；缺失则跳过。 */
    private static void copyOptionalScripts(String prefix, Path dest) throws IOException {
        ClassLoader loader = SkillsBundledInstall.class.getClassLoader();
        URL scriptsUrl = loader.getResource(prefix + "scripts");
        if (scriptsUrl == null) {
            return;
        }
        if ("file".equals(scriptsUrl.getProtocol())) {
            Path scriptsDir;
            try {
                scriptsDir = Path.of(scriptsUrl.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("bad scripts dir: " + scriptsUrl, e);
            }
            if (!Files.isDirectory(scriptsDir)) {
                return;
            }
            try (var walk = Files.walk(scriptsDir)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    String rel = scriptsDir.relativize(p).toString().replace('\\', '/');
                    copyResourceFile(prefix + "scripts/" + rel, dest.resolve("scripts").resolve(rel));
                }
            }
        } else {
            try (JarFile jar = ((JarURLConnection) scriptsUrl.openConnection()).getJarFile()) {
                String scriptsPrefix = prefix + "scripts/";
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(scriptsPrefix) && !entry.isDirectory() && !entryName.contains("..")) {
                        String rel = entryName.substring(scriptsPrefix.length());
                        copyResourceFile(entryName, dest.resolve("scripts").resolve(rel));
                    }
                }
            }
        }
    }

    private static void copyResourceFile(String resourcePath, Path target) throws IOException {
        try (InputStream in = SkillsBundledInstall.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("missing resource: " + resourcePath);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
