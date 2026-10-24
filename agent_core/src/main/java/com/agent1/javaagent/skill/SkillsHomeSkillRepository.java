package com.agent1.javaagent.skill;

import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * {@code skills-home} 当前版本目录的仓库视图。
 * 是 {@code load_skill} 统一优先级表中的「已装官方」层；registry / bundled 安装都落在这里。
 */
public final class SkillsHomeSkillRepository implements SkillRepository {

    private final Path agentRoot;

    public SkillsHomeSkillRepository(Path agentRoot) {
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public List<Skill> list() {
        if (agentRoot == null) {
            return List.of();
        }
        List<Skill> all = new ArrayList<>();
        for (String name : SkillsHomeInstaller.readIndex(agentRoot).keySet()) {
            Skill skill = load(name);
            if (skill != null) {
                all.add(skill);
            }
        }
        all.sort(Comparator.comparing(Skill::getId));
        return all;
    }

    @Override
    public Skill load(String skillId) {
        if (agentRoot == null
            || skillId == null
            || skillId.isBlank()
            || skillId.startsWith("/")
            || skillId.contains("..")) {
            return null;
        }
        Path dir = SkillsHomeInstaller.installedDir(agentRoot, skillId.trim());
        if (dir == null || !Files.isRegularFile(dir.resolve("SKILL.md"))) {
            return null;
        }
        try {
            Skill parsed = MarkdownSkillParser.parse(dir);
            // 目录名带版本（<name>@<version>），对外 id 用不带版本的 skill 名
            return new Skill(
                skillId.trim(),
                parsed.getDescription(),
                parsed.getRootDir(),
                parsed.getInstructions(),
                parsed.getResources()
            );
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String readResource(String skillId, String path) {
        if (path == null || path.equals(".") || path.equals("./") || path.startsWith("/")) {
            return "Error: path must be a relative file (e.g. 'SKILL.md'), got: " + path;
        }
        Skill sk = load(skillId);
        if (sk == null) {
            return "Error: skill not found: " + skillId;
        }
        Path base = Path.of(sk.getRootDir());
        Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            return "Error: path outside skill dir";
        }
        if (!Files.isRegularFile(resolved)) {
            return "Error: resource not found: " + path + "\nAvailable: " + listResources(skillId);
        }
        try {
            return PathIo.readString(resolved);
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public List<String> listResources(String skillId) {
        Skill sk = load(skillId);
        if (sk == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        names.add("SKILL.md");
        names.addAll(sk.getResources());
        return names;
    }
}
