package com.agent1.javaagent.skill;

import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 从 {@link SkillsHomePaths} 当前版本目录加载 SKILL.md。 */
public final class SkillsHomeLoader {

    public static final String SOURCE_LABEL = "skills_home";

    private static final Logger LOG = Logger.getLogger(SkillsHomeLoader.class.getName());

    private SkillsHomeLoader() {
    }

    public static List<AgentSkill> loadInstalled(Path agentRoot) {
        if (agentRoot == null) {
            return List.of();
        }
        Path skillsDir = SkillsHomePaths.skillsDir(agentRoot);
        if (!Files.isDirectory(skillsDir)) {
            return List.of();
        }
        Map<String, String> index = SkillsHomeInstaller.readIndex(agentRoot);
        if (index.isEmpty()) {
            return List.of();
        }
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(dirs::add);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "list skills-home failed: " + skillsDir, e);
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
            if (!dirName.substring(at + 1).equals(index.get(name))) {
                continue;
            }
            Path skillFile = dir.resolve("SKILL.md");
            if (!Files.isRegularFile(skillFile)) {
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
                LOG.log(Level.WARNING, "parse skill failed: " + skillFile, e);
            }
        }
        return skills;
    }
}
