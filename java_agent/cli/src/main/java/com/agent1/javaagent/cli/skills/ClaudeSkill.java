package com.agent1.javaagent.cli.skills;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ClaudeSkill {
    private final String name;
    private final String description;
    private final String content;
    private final Path sourcePath;
    private final Map<String, String> frontmatter;

    public ClaudeSkill(
        String name,
        String description,
        String content,
        Path sourcePath,
        Map<String, String> frontmatter
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.content = content == null ? "" : content;
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.frontmatter = Collections.unmodifiableMap(
            new LinkedHashMap<>(frontmatter == null ? Map.of() : frontmatter)
        );
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Map<String, String> getFrontmatter() {
        return frontmatter;
    }
}
