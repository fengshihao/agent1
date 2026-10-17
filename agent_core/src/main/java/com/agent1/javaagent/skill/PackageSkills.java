package com.agent1.javaagent.skill;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.capability.CapabilityRecord;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 已安装微智 Package 里随包分发的 skill：包 manifest 的 {@code skill} 字段指向 SKILL.md，
 * 只认 {@code packages-index.json} 的当前版本目录。
 * 安装侧（WeizhiBundledPackages / WeizhiPackageSync）装完包后调
 * {@link #syncCapabilityCards} 把 {@code kind=skill} 卡整批写入 find_caps
 * （source={@value #SOURCE}）；{@link AgentSkillLoader#loadMerged} 从同一批文件加载正文。
 */
public final class PackageSkills {

    public static final String SOURCE = "package_skill";
    public static final String SOURCE_LABEL = "package";

    private static final Logger LOG = Logger.getLogger(PackageSkills.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PackageSkills() {
    }

    /** 装完包后刷新 find_caps 里的随包 skill 卡。失败只告警，不打断安装链路。 */
    public static void syncCapabilityCards(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        try {
            CapabilityIndexStore.replaceSource(agentRoot, SOURCE, cardsFor(loadInstalled(agentRoot)));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "sync package skill cards failed", e);
        }
    }

    /** 当前版本目录里声明了 {@code skill} 的包。索引缺失或损坏时返回空，不猜版本。 */
    public static List<AgentSkill> loadInstalled(Path agentRoot) {
        if (agentRoot == null) {
            return List.of();
        }
        Path home = agentRoot.toAbsolutePath().normalize().resolve("weizhi-home");
        Path packagesDir = home.resolve("packages");
        if (!Files.isDirectory(packagesDir)) {
            return List.of();
        }
        Map<String, String> current = currentIndex(home);
        if (current.isEmpty()) {
            return List.of();
        }
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.list(packagesDir)) {
            stream.filter(Files::isDirectory)
                .filter(dir -> dir.getFileName() != null)
                .sorted(Comparator.comparing(dir -> dir.getFileName().toString()))
                .forEach(dirs::add);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "list installed packages failed: " + packagesDir, e);
            return List.of();
        }
        AgentSkillLoader loader = new AgentSkillLoader();
        List<AgentSkill> skills = new ArrayList<>();
        for (Path dir : dirs) {
            Path fileName = dir.getFileName();
            if (fileName == null) {
                continue;
            }
            String dirName = fileName.toString();
            int at = dirName.indexOf('@');
            if (at <= 0 || at + 1 >= dirName.length()) {
                continue;
            }
            String name = dirName.substring(0, at);
            if (!dirName.substring(at + 1).equals(current.get(name))) {
                continue;
            }
            Path skillFile = declaredSkillFile(dir);
            if (skillFile == null) {
                continue;
            }
            try {
                AgentSkillLoader.ParsedSkill parsed = loader.parseSkill(PathIo.readString(skillFile), name);
                skills.add(
                    new AgentSkill(
                        parsed.name(),
                        parsed.description(),
                        parsed.content(),
                        skillFile,
                        SOURCE_LABEL,
                        parsed.frontmatter()
                    )
                );
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "parse package skill failed: " + skillFile, e);
            }
        }
        return skills;
    }

    static List<CapabilityRecord> cardsFor(List<AgentSkill> skills) {
        List<CapabilityRecord> records = new ArrayList<>();
        for (AgentSkill skill : skills) {
            records.add(
                new CapabilityRecord(
                    "skill." + skill.name(),
                    "skill",
                    skill.name(),
                    skill.description(),
                    skill.frontmatter().getOrDefault("tags", "").trim(),
                    "any",
                    "skill:" + skill.name(),
                    "",
                    "",
                    "",
                    SOURCE,
                    1.3
                )
            );
        }
        return records;
    }

    /** manifest 里 {@code skill} 字段指向的文件；只接受包目录内的相对文件名。 */
    private static Path declaredSkillFile(Path packageDir) {
        Path manifest = packageDir.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        String declared;
        try {
            JsonNode node = MAPPER.readTree(PathIo.readString(manifest));
            declared = node.path("skill").asText("").trim();
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (declared.isEmpty() || declared.contains("/") || declared.contains("\\") || declared.contains("..")) {
            return null;
        }
        Path skillFile = packageDir.resolve(declared);
        return Files.isRegularFile(skillFile) ? skillFile : null;
    }

    private static Map<String, String> currentIndex(Path weizhiHome) {
        Map<String, String> index = new LinkedHashMap<>();
        Path file = weizhiHome.resolve("packages-index.json");
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
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "read packages-index.json failed: " + file, e);
        }
        return index;
    }
}
