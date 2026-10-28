package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.skill.SkillsCapabilitySync;
import com.agent1.javaagent.skill.SkillsHomeInstaller;
import com.agent1.javaagent.skill.SkillsHomePaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 从固化测试 fixture（classpath {@code weizhi-test-skills/}）安装 skill 到 {@code skills-home}。
 * <p>
 * 生产路径无 classpath 预装（skill 靠 registry 云装并自动刷卡），本类仅服务集成测试。
 */
public final class WeizhiTestSkills {

    public static final List<String> FIXTURE_SKILL_NAMES =
        List.of("html2pptx", "travel-itinerary", "pptx", "video-edit");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE_RESOURCE = "weizhi-test-skills";
    private static volatile Path fixtureRoot;

    private WeizhiTestSkills() {
    }

    /** 安装 fixture 全部 skill 并刷新 skill 能力卡；等价于历史上的 {@code SkillsBundledInstall.syncFromClasspath}。 */
    public static void syncFromFixture(Path agentRoot) throws IOException {
        Files.createDirectories(SkillsHomePaths.home(agentRoot));
        for (String name : FIXTURE_SKILL_NAMES) {
            install(agentRoot, name);
        }
        SkillsCapabilitySync.syncCapabilityCards(agentRoot);
    }

    public static void install(Path agentRoot, String name) throws IOException {
        Path source = resolveSkillSource(name);
        String version = readVersion(source.resolve("manifest.json"), name);
        SkillsHomeInstaller.installFromDirectory(agentRoot, name, version, source);
    }

    public static Path resolveSkillSource(String name) throws IOException {
        Path dir = resolveFixtureRoot().resolve(name);
        if (!Files.isDirectory(dir) || !Files.isRegularFile(dir.resolve("manifest.json"))) {
            throw new IOException("weizhi test skill source missing: " + dir);
        }
        return dir.toAbsolutePath().normalize();
    }

    private static Path resolveFixtureRoot() throws IOException {
        Path cached = fixtureRoot;
        if (cached != null) {
            return cached;
        }
        synchronized (WeizhiTestSkills.class) {
            if (fixtureRoot == null) {
                fixtureRoot =
                    WeizhiTestPackages.materializeClasspathFixture(FIXTURE_RESOURCE, "weizhi-test-skills-");
            }
            return fixtureRoot;
        }
    }

    private static String readVersion(Path manifest, String expectedName) throws IOException {
        JsonNode node = MAPPER.readTree(manifest.toFile());
        String manifestName = node.path("name").asText("").trim();
        if (!expectedName.equals(manifestName)) {
            throw new IOException(expectedName + ": manifest.name must match directory (" + manifestName + ")");
        }
        String version = node.path("version").asText("").trim();
        if (version.isEmpty()) {
            throw new IOException(expectedName + ": manifest.version required");
        }
        return version;
    }
}
