package com.agent1.javaagent.skill;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Claude Code 兼容 SKILL.md 条目。 */
public final class AgentSkill {

    private final String name;
    private final String description;
    private final String content;
    private final Path sourcePath;
    private final String sourceLabel;
    private final Map<String, String> frontmatter;

    public AgentSkill(
        String name,
        String description,
        String content,
        Path sourcePath,
        String sourceLabel,
        Map<String, String> frontmatter
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.content = content == null ? "" : content;
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        this.frontmatter = Collections.unmodifiableMap(
            new LinkedHashMap<>(frontmatter == null ? Map.of() : frontmatter)
        );
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String content() {
        return content;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public Map<String, String> frontmatter() {
        return frontmatter;
    }
}
