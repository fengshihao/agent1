package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.skill.AgentSkill;
import com.agent1.javaagent.skill.AgentSkillLoader;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Locale;

/** 生产力路径 Skill：list / read（6.4，合并 project + catalog + local）。 */
public final class ProductivitySkillTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    private final Path projectRoot;
    private final AgentSkillLoader loader = new AgentSkillLoader();

    public ProductivitySkillTool(Path agentRoot, Path projectRoot) {
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return "List or read merged skills (project .claude/skills, shared/catalog, shared/local).";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        ArrayNode actionEnum = MAPPER.createArrayNode().add("list").add("read");
        ObjectNode actionNode = MAPPER.createObjectNode();
        actionNode.put("type", "string");
        actionNode.set("enum", actionEnum);
        properties.set("action", actionNode);
        properties.set(
            "skill_name",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Skill name for action=read")
        );
        schema.set("properties", properties);
        schema.set("required", MAPPER.createArrayNode().add("action"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        String action = parameters.path("action").asText("").trim().toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            return listSkills();
        }
        if ("read".equals(action)) {
            return readSkill(parameters.path("skill_name").asText("").trim());
        }
        return ToolExecutionResult.text("错误：action 支持 list、read");
    }

    private ToolExecutionResult listSkills() {
        AgentSkillLoader.SkillLoadResult result = loader.loadMerged(agentRoot, projectRoot);
        StringBuilder out = new StringBuilder();
        out.append("skills (merged): ").append(result.skills().size()).append('\n');
        for (AgentSkill skill : result.skills()) {
            out.append("- ")
                .append(skill.name())
                .append(" [")
                .append(skill.sourceLabel())
                .append("] ")
                .append(skill.description())
                .append('\n');
        }
        for (String warning : result.warnings()) {
            out.append("(warn) ").append(warning).append('\n');
        }
        return ToolExecutionResult.text(out.toString().trim());
    }

    private ToolExecutionResult readSkill(String skillName) {
        if (skillName.isBlank()) {
            return ToolExecutionResult.text("错误：action=read 需要 skill_name");
        }
        AgentSkillLoader.SkillLoadResult result = loader.loadMerged(agentRoot, projectRoot);
        for (AgentSkill skill : result.skills()) {
            if (skill.name().equalsIgnoreCase(skillName)) {
                StringBuilder out = new StringBuilder();
                out.append("SKILL: ").append(skill.name()).append('\n');
                out.append("source: ").append(skill.sourceLabel()).append('\n');
                out.append("path: ").append(skill.sourcePath()).append("\n\n");
                out.append(skill.content());
                return ToolExecutionResult.text(out.toString().trim());
            }
        }
        return ToolExecutionResult.text("未找到 skill: " + skillName + "（先用 action=list）");
    }
}
