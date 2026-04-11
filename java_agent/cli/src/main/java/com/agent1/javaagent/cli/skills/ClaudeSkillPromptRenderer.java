package com.agent1.javaagent.cli.skills;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClaudeSkillPromptRenderer {
    private static final Pattern ARG_INDEX_PATTERN = Pattern.compile("\\$ARGUMENTS\\[(\\d+)]");
    private static final Pattern SHORT_ARG_INDEX_PATTERN = Pattern.compile("\\$(\\d+)");

    private ClaudeSkillPromptRenderer() {
    }

    public static String render(ClaudeSkill skill, String rawArguments) {
        String content = skill.getContent();
        String args = rawArguments == null ? "" : rawArguments.trim();
        List<String> argList = args.isEmpty() ? List.of() : List.of(args.split("\\s+"));
        boolean containsAllArgsPlaceholder = content.contains("$ARGUMENTS");

        String rendered = content.replace("${CLAUDE_SKILL_DIR}", skillDirectory(skill));
        rendered = replaceByIndex(rendered, ARG_INDEX_PATTERN, argList);
        rendered = replaceByIndex(rendered, SHORT_ARG_INDEX_PATTERN, argList);
        rendered = rendered.replace("$ARGUMENTS", args);

        if (!args.isEmpty() && !containsAllArgsPlaceholder) {
            rendered = rendered + "\n\nARGUMENTS: " + args;
        }
        return rendered;
    }

    private static String replaceByIndex(String input, Pattern pattern, List<String> args) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = index >= 0 && index < args.size() ? args.get(index) : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String skillDirectory(ClaudeSkill skill) {
        Path parent = skill.getSourcePath().getParent();
        return parent == null ? "" : parent.toString();
    }
}
