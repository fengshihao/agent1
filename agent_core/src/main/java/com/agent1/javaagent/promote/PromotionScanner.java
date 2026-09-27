package com.agent1.javaagent.promote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** staging 扫描与轻量规则（阶段 6.2，审查恒通过）。 */
final class PromotionScanner {

    private static final long MAX_FILE_BYTES = 512L * 1024L;
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|secret|password|token\\s*=|sk-[a-z0-9]{16,})"
    );

    private PromotionScanner() {
    }

    record StagedSkill(String dirName, Path skillMd) {
    }

    record StagedScript(String fileName, Path scriptFile, Path metaFile) {
    }

    record ScanResult(List<StagedSkill> skills, List<StagedScript> scripts, List<String> rejections) {
    }

    static ScanResult scan(Path workspaceRoot) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        List<StagedSkill> skills = new ArrayList<>();
        List<StagedScript> scripts = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        Path skillsRoot = root.resolve("staging/skills");
        if (Files.isDirectory(skillsRoot)) {
            try (Stream<Path> dirs = Files.list(skillsRoot)) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    Path skillMd = dir.resolve("SKILL.md");
                    if (!Files.isRegularFile(skillMd)) {
                        rejections.add(dir.getFileName() + ": 缺少 SKILL.md");
                        return;
                    }
                    try {
                        scanFileContent(skillMd, rejections);
                    } catch (IOException e) {
                        rejections.add(dir.getFileName() + ": " + e.getMessage());
                        return;
                    }
                    skills.add(new StagedSkill(dir.getFileName().toString(), skillMd));
                });
            }
        }

        Path scriptsRoot = root.resolve("staging/scripts");
        if (Files.isDirectory(scriptsRoot)) {
            try (Stream<Path> files = Files.list(scriptsRoot)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".js")) {
                        return;
                    }
                    try {
                        scanFileContent(file, rejections);
                    } catch (IOException e) {
                        rejections.add(name + ": " + e.getMessage());
                        return;
                    }
                    Path meta = scriptsRoot.resolve(stripJs(name) + ".meta.json");
                    Path metaFile = Files.isRegularFile(meta) ? meta : null;
                    scripts.add(new StagedScript(name, file, metaFile));
                });
            }
        }

        return new ScanResult(skills, scripts, rejections);
    }

    private static String stripJs(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".js")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static void scanFileContent(Path file, List<String> rejections) throws IOException {
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            rejections.add(file.getFileName() + ": 超过 " + MAX_FILE_BYTES + " 字节");
            return;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (SECRET_PATTERN.matcher(text).find()) {
            rejections.add(file.getFileName() + ": 疑似密钥/敏感内容，请去敏后再 promote");
        }
    }
}
