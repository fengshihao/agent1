package com.agent1.javaagent.tool.skill;

import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;

/** 按路径读 skill 资源。技能目录从哪来由宿主传入。 */
public final class LoadSkillTool {

    @FunctionalInterface
    public interface ResourceReader {
        String read(String skillId, String path);
    }

    private final ResourceReader reader;

    public LoadSkillTool(ResourceReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("skill reader required");
        }
        this.reader = reader;
    }

    @Tool(
        name = "load_skill_through_path",
        description = "按路径读取 skill 的 SKILL.md 或 references 下资源。",
        readOnly = true,
        concurrencySafe = true
    )
    public String load(
        @ToolParam(name = "skillId", description = "skill 目录名") String skillId,
        @ToolParam(name = "path", description = "如 SKILL.md 或 references/x.md") String path
    ) {
        return reader.read(skillId, path);
    }
}
