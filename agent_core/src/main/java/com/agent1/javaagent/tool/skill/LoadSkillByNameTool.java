package com.agent1.javaagent.tool.skill;

import com.agent1.javaagent.skill.AgentSkill;
import com.agent1.javaagent.skill.AgentSkillLoader;
import com.agent1.javaagent.skill.SkillsRegistryInstall;
import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.file.Path;

/** 按 skill 名加载 SKILL.md（生产力主路径 {@code load_skill}）。 */
public final class LoadSkillByNameTool {

    public static final String TOOL_NAME = "load_skill";

    private final Path agentRoot;
    private final Path projectRoot;

    public LoadSkillByNameTool(Path agentRoot, Path projectRoot) {
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
        this.projectRoot = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
    }

    @Tool(
        name = TOOL_NAME,
        description = "加载官方或已安装 Skill 的 SKILL.md 正文。复杂任务先对照系统提示里的 Skill 目录，命中则 load_skill 再执行。",
        readOnly = true,
        concurrencySafe = true
    )
    public String load(@ToolParam(name = "name", description = "skill 名，如 travel-itinerary、html2pptx") String name) {
        if (name == null || name.isBlank()) {
            return "错误：name 不能为空";
        }
        String skillName = name.trim();
        try {
            SkillsRegistryInstall.ensure(agentRoot, skillName);
        } catch (IOException e) {
            return "错误：" + e.getMessage();
        }
        AgentSkillLoader loader = new AgentSkillLoader();
        AgentSkill skill =
            loader.loadMerged(agentRoot, projectRoot == null ? agentRoot : projectRoot)
                .skills()
                .stream()
                .filter(s -> skillName.equalsIgnoreCase(s.name()))
                .findFirst()
                .orElse(null);
        if (skill == null) {
            return "错误：未找到 skill「" + skillName + "」";
        }
        String body = skill.content();
        if (body == null || body.isBlank()) {
            try {
                body = PathIo.readString(skill.sourcePath());
            } catch (IOException e) {
                return "错误：读取 SKILL 失败 " + e.getMessage();
            }
        }
        String header =
            "[skill] "
                + skill.name()
                + " loaded (source: "
                + skill.sourceLabel()
                + ")\n\n";
        return header + body.trim();
    }
}
