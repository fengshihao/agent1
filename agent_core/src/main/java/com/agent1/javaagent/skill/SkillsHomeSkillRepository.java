package com.agent1.javaagent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code load_skill_through_path} 从 {@code skills-home} 当前版本目录读资源。 */
public final class SkillsHomeSkillRepository implements SkillRepository {

    private final Path agentRoot;

    public SkillsHomeSkillRepository(Path agentRoot) {
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public List<Skill> list() {
        return List.of();
    }

    @Override
    public Skill load(String skillId) {
        return null;
    }

    @Override
    public String readResource(String skillId, String path) {
        if (agentRoot == null || skillId == null || skillId.isBlank()) {
            return "Error: skill not found: " + skillId;
        }
        if (path == null || path.equals(".") || path.equals("./") || path.startsWith("/")) {
            return "Error: path must be a relative file (e.g. 'SKILL.md'), got: " + path;
        }
        Path base = SkillsHomeInstaller.installedDir(agentRoot, skillId.trim());
        if (base == null || !Files.isDirectory(base)) {
            return "Error: skill not found: " + skillId;
        }
        Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            return "Error: path outside skill dir";
        }
        if (!Files.isRegularFile(resolved)) {
            return "Error: resource not found: " + path;
        }
        try {
            return new String(Files.readAllBytes(resolved), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public List<String> listResources(String skillId) {
        Path base = SkillsHomeInstaller.installedDir(agentRoot, skillId == null ? "" : skillId.trim());
        if (base == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        if (Files.isRegularFile(base.resolve("SKILL.md"))) {
            names.add("SKILL.md");
        }
        return names;
    }
}
