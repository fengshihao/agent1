package com.agent1.javaagent.tool.skill;

import com.agent1.javaagent.skill.FileSystemSkillRepository;
import com.agent1.javaagent.skill.Skill;
import com.agent1.javaagent.skill.SkillRepository;
import com.agent1.javaagent.skill.SkillsHomeSkillRepository;
import com.agent1.javaagent.skill.SkillsRegistryInstall;
import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code load_skill}：按名加载 Skill（默认 SKILL.md 剧本；{@code path} 可读 skill 内资源）。
 *
 * 统一优先级表（先命中先用）：local → project → workspace → 宿主特有源（如 Android assets）
 * → skills-home（已装官方）→ catalog。全表未命中时自动安装（registry 优先、bundled 兜底，
 * 均落 skills-home）再读。本地覆盖官方，同名不合并。
 * 旧 {@code load_skill_through_path}（读资源）已并入本工具的 {@code path} 参数。
 */
public final class LoadSkillByNameTool {

    public static final String TOOL_NAME = "load_skill";
    static final String SKILL_MD = "SKILL.md";

    private record Source(String label, SkillRepository repo) {
    }

    private final Path agentRoot;
    private final List<Source> sources;

    public LoadSkillByNameTool(Path agentRoot, Path projectRoot) {
        this(agentRoot, projectRoot, null, List.of());
    }

    public LoadSkillByNameTool(Path agentRoot, Path projectRoot, Path workspaceRoot) {
        this(agentRoot, projectRoot, workspaceRoot, List.of());
    }

    public LoadSkillByNameTool(
        Path agentRoot,
        Path projectRoot,
        Path workspaceRoot,
        List<SkillRepository> hostRepositories
    ) {
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
        this.sources = buildSources(this.agentRoot, projectRoot, workspaceRoot, hostRepositories);
    }

    private static List<Source> buildSources(
        Path agentRoot,
        Path projectRoot,
        Path workspaceRoot,
        List<SkillRepository> hostRepositories
    ) {
        List<Source> sources = new ArrayList<>();
        if (agentRoot != null) {
            sources.add(new Source(
                "local",
                new FileSystemSkillRepository(agentRoot.resolve("shared/local/skills"))
            ));
        }
        if (projectRoot != null) {
            sources.add(new Source(
                "project",
                new FileSystemSkillRepository(
                    projectRoot.toAbsolutePath().normalize().resolve(".claude/skills")
                )
            ));
        }
        if (workspaceRoot != null) {
            sources.add(new Source(
                "workspace",
                new FileSystemSkillRepository(
                    workspaceRoot.toAbsolutePath().normalize().resolve("skills")
                )
            ));
        }
        List<SkillRepository> host = hostRepositories == null ? List.of() : hostRepositories;
        for (SkillRepository repo : host) {
            sources.add(new Source("host", repo));
        }
        if (agentRoot != null) {
            sources.add(new Source("skills_home", new SkillsHomeSkillRepository(agentRoot)));
            sources.add(new Source(
                "catalog",
                new FileSystemSkillRepository(agentRoot.resolve("shared/catalog/skills"))
            ));
        }
        return List.copyOf(sources);
    }

    @Tool(
        name = TOOL_NAME,
        description = "加载官方或已安装 Skill：缺省读 SKILL.md 剧本；path 可读 skill 内资源（references/…、scripts/…）。"
            + "本地与已装 skill 都没有时自动从官方 registry 安装再读。复杂任务先对照系统提示里的 Skill 目录，命中则 load_skill 再执行。",
        readOnly = true,
        concurrencySafe = true
    )
    public String load(
        @ToolParam(name = "name", description = "skill 名，如 travel-itinerary、html2pptx") String name,
        @ToolParam(
            name = "path",
            description = "可选：skill 内相对路径，如 references/x.md；缺省读 SKILL.md",
            required = false
        ) String path
    ) {
        if (name == null || name.isBlank()) {
            return "错误：name 不能为空";
        }
        String skillName = name.trim();
        String resource = normalizeResource(path);
        if (resource == null) {
            return "错误：path 必须是 skill 内的相对文件路径";
        }
        Source found = find(skillName);
        if (found == null) {
            return installThenLoad(skillName, resource);
        }
        return readFrom(found, skillName, resource);
    }

    private static String normalizeResource(String path) {
        if (path == null || path.isBlank()) {
            return SKILL_MD;
        }
        String p = path.trim();
        if (p.startsWith("/") || p.contains("..") || p.equals(".") || p.equals("./")) {
            return null;
        }
        return p;
    }

    /** 按优先级找第一个持有该 skill 的源。 */
    private Source find(String skillName) {
        for (Source source : sources) {
            if (source.repo().load(skillName) != null) {
                return source;
            }
        }
        return null;
    }

    /** 全表未命中：registry → bundled 自动安装（落 skills-home）后重读。 */
    private String installThenLoad(String skillName, String resource) {
        if (agentRoot == null) {
            return "错误：未找到 skill「" + skillName + "」";
        }
        try {
            SkillsRegistryInstall.ensure(agentRoot, skillName);
        } catch (IOException e) {
            return "错误：" + e.getMessage();
        }
        Source found = find(skillName);
        if (found == null) {
            return "错误：未找到 skill「" + skillName + "」";
        }
        return readFrom(found, skillName, resource);
    }

    /** 从命中源读取：SKILL.md 返回正文（去 frontmatter），其余路径原样返回。 */
    private String readFrom(Source source, String skillName, String resource) {
        Skill skill = source.repo().load(skillName);
        if (skill == null) {
            return "错误：未找到 skill「" + skillName + "」";
        }
        if (SKILL_MD.equals(resource)) {
            String body = skill.getInstructions();
            if (body == null || body.isBlank()) {
                body = source.repo().readResource(skillName, SKILL_MD);
            }
            return "[skill] " + skill.getId() + " loaded (source: " + source.label() + ")\n\n" + body;
        }
        String text = source.repo().readResource(skillName, resource);
        return "[skill] " + skill.getId() + " " + resource + " (source: " + source.label() + ")\n\n" + text;
    }
}
