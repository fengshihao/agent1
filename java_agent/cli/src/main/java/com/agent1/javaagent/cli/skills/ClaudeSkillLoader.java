package com.agent1.javaagent.cli.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ClaudeSkillLoader {
    public SkillLoadResult loadFromProjectRoot(Path projectRoot) {
        Path skillsRoot = projectRoot.resolve(".claude").resolve("skills");
        return loadFromSkillsRoot(skillsRoot);
    }

    public SkillLoadResult loadFromSkillsRoot(Path skillsRoot) {
        List<ClaudeSkill> skills = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!Files.isDirectory(skillsRoot)) {
            return new SkillLoadResult(skills, warnings);
        }

        try (var skillDirs = Files.list(skillsRoot)) {
            skillDirs
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(skillDir -> loadSingleSkill(skillDir, skills, warnings));
        } catch (IOException e) {
            warnings.add("扫描 skills 目录失败: " + e.getMessage());
        }

        return new SkillLoadResult(skills, warnings);
    }

    private void loadSingleSkill(Path skillDir, List<ClaudeSkill> skills, List<String> warnings) {
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return;
        }
        try {
            String raw = Files.readString(skillFile, StandardCharsets.UTF_8);
            ParsedSkill parsed = parseSkill(raw, skillDir.getFileName().toString());
            skills.add(
                new ClaudeSkill(
                    parsed.name(),
                    parsed.description(),
                    parsed.content(),
                    skillFile,
                    parsed.frontmatter()
                )
            );
        } catch (Exception e) {
            warnings.add("解析 skill 失败(" + skillFile + "): " + e.getMessage());
        }
    }

    ParsedSkill parseSkill(String raw, String fallbackName) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(fallbackName, "fallbackName");

        FrontmatterSplit split = splitFrontmatter(raw);
        Map<String, String> frontmatter = parseFrontmatter(split.frontmatter());

        String name = firstNonBlank(frontmatter.get("name"), fallbackName);
        String description = firstNonBlank(frontmatter.get("description"), "");
        return new ParsedSkill(name, description, split.content(), frontmatter);
    }

    private FrontmatterSplit splitFrontmatter(String raw) {
        String normalized = raw.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new FrontmatterSplit("", normalized);
        }

        int secondMarkerStart = normalized.indexOf("\n---\n", 4);
        if (secondMarkerStart < 0) {
            throw new IllegalArgumentException("frontmatter 未闭合");
        }

        String frontmatter = normalized.substring(4, secondMarkerStart);
        String content = normalized.substring(secondMarkerStart + "\n---\n".length());
        return new FrontmatterSplit(frontmatter, content);
    }

    private Map<String, String> parseFrontmatter(String frontmatterText) {
        Map<String, String> values = new LinkedHashMap<>();
        if (frontmatterText.isBlank()) {
            return values;
        }

        String[] lines = frontmatterText.split("\n", -1);
        String currentKey = null;
        StringBuilder currentMultiLine = null;
        boolean currentFolded = false;

        for (String line : lines) {
            if (line.isBlank()) {
                if (currentMultiLine != null) {
                    currentMultiLine.append(currentFolded ? " " : "\n");
                }
                continue;
            }

            String trimmed = line.trim();
            boolean looksLikeKey = !line.startsWith(" ") && !line.startsWith("\t") && trimmed.contains(":");

            if (looksLikeKey) {
                if (currentKey != null && currentMultiLine != null) {
                    values.put(currentKey, currentMultiLine.toString().trim());
                }

                int idx = trimmed.indexOf(':');
                String key = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                String valuePart = trimmed.substring(idx + 1).trim();

                if (">".equals(valuePart) || ">-".equals(valuePart) || "|".equals(valuePart) || "|-".equals(valuePart)) {
                    currentKey = key;
                    currentFolded = valuePart.startsWith(">");
                    currentMultiLine = new StringBuilder();
                    continue;
                }

                values.put(key, stripQuotes(valuePart));
                currentKey = null;
                currentMultiLine = null;
                currentFolded = false;
                continue;
            }

            if (currentKey != null && currentMultiLine != null) {
                String valueLine = line.startsWith(" ") ? line.substring(1) : line;
                currentMultiLine.append(currentFolded ? valueLine.trim() + " " : valueLine + "\n");
            } else {
                throw new IllegalArgumentException("frontmatter 格式不合法: " + line);
            }
        }

        if (currentKey != null && currentMultiLine != null) {
            values.put(currentKey, currentMultiLine.toString().trim());
        }

        return values;
    }

    private String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return fallback;
    }

    public record SkillLoadResult(List<ClaudeSkill> skills, List<String> warnings) {
        public SkillLoadResult {
            skills = List.copyOf(skills);
            warnings = List.copyOf(warnings);
        }
    }

    record ParsedSkill(String name, String description, String content, Map<String, String> frontmatter) {
        ParsedSkill {
            frontmatter = Map.copyOf(frontmatter);
        }
    }

    private record FrontmatterSplit(String frontmatter, String content) {
    }
}
