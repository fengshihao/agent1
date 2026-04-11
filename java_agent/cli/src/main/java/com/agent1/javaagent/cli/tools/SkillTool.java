package com.agent1.javaagent.cli.tools;

import com.agent1.javaagent.cli.skills.ClaudeSkill;
import com.agent1.javaagent.cli.skills.ClaudeSkillLoader;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillTool implements AgentTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 10;
    private static final String GITHUB_SEARCH_API = "https://api.github.com/search/repositories";
    private static final String DEFAULT_DEST_ROOT = ".claude/skills";
    private static final Pattern GITHUB_SLUG = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern GITHUB_URL = Pattern.compile("^https?://github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?:/.*)?$");

    private final Path workspaceRoot;
    private final HttpClient httpClient;
    private final ClaudeSkillLoader skillLoader;

    public SkillTool(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.skillLoader = new ClaudeSkillLoader();
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return "Manage skills with action=search|install|read|uninstall.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();

        ArrayNode actionEnum = MAPPER.createArrayNode().add("search").add("install").add("read").add("uninstall");
        ObjectNode actionNode = MAPPER.createObjectNode();
        actionNode.put("type", "string");
        actionNode.set("enum", actionEnum);
        actionNode.put("description", "Skill action: search, install, read");
        properties.set("action", actionNode);
        properties.set(
            "query",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Search query for action=search")
        );
        properties.set(
            "limit",
            MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Search result count for action=search, default 5, max 10")
        );
        properties.set(
            "source",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Source for action=install: GitHub repo (owner/repo or URL) or local dir path")
        );
        properties.set(
            "skill_name",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Skill name for action=read/uninstall, or target skill for action=install")
        );
        properties.set(
            "overwrite",
            MAPPER.createObjectNode()
                .put("type", "boolean")
                .put("description", "Overwrite existing installed skill for action=install")
        );
        properties.set(
            "destination_root",
            MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Install root under workspace for action=install, default .claude/skills")
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
        String action = parameters.path("action").asText("").trim().toLowerCase(Locale.ROOT);
        if (action.isEmpty()) {
            return ToolExecutionResult.text("错误：action 不能为空，支持 search/install/read/uninstall");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        try {
            return switch (action) {
                case "search" -> search(parameters, cancellationToken);
                case "install" -> install(parameters, cancellationToken);
                case "read" -> readSkill(parameters, cancellationToken);
                case "uninstall" -> uninstall(parameters, cancellationToken);
                default -> ToolExecutionResult.text("错误：不支持的 action=" + action + "，支持 search/install/read/uninstall");
            };
        } catch (Exception e) {
            return ToolExecutionResult.text("错误：skill 工具执行失败: " + e.getMessage());
        }
    }

    private ToolExecutionResult search(JsonNode parameters, CancellationToken cancellationToken) throws Exception {
        String query = parameters.path("query").asText("").trim();
        if (query.isEmpty()) {
            return ToolExecutionResult.text("错误：action=search 时 query 不能为空");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        int limit = parameters.path("limit").asInt(DEFAULT_SEARCH_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_SEARCH_LIMIT;
        }
        limit = Math.min(limit, MAX_SEARCH_LIMIT);

        String githubQuery = query + " (claude skill OR cursor skill OR SKILL.md) in:name,description,readme";
        String encodedQuery = URLEncoder.encode(githubQuery, StandardCharsets.UTF_8);
        URI uri = URI.create(GITHUB_SEARCH_API + "?q=" + encodedQuery + "&sort=stars&order=desc&per_page=" + limit);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "java-agent-skill-tool")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ToolExecutionResult.text("错误：GitHub 搜索失败，status=" + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return ToolExecutionResult.text("没有找到匹配的技能仓库。");
        }

        List<String> lines = new ArrayList<>();
        lines.add("搜索结果（可用于 action=install 的 source）:");
        int idx = 1;
        for (JsonNode item : items) {
            String fullName = item.path("full_name").asText("");
            String htmlUrl = item.path("html_url").asText("");
            String description = item.path("description").asText("");
            int stars = item.path("stargazers_count").asInt(0);
            if (fullName.isBlank()) {
                continue;
            }
            lines.add(
                idx + ". " + fullName
                    + " | stars=" + stars
                    + (description.isBlank() ? "" : " | " + description.replace('\n', ' ').trim())
                    + "\n   " + htmlUrl
            );
            idx++;
        }
        if (idx == 1) {
            return ToolExecutionResult.text("没有找到匹配的技能仓库。");
        }
        lines.add("");
        lines.add("安装示例：skill {\"action\":\"install\",\"source\":\"owner/repo\"}");
        return ToolExecutionResult.text(String.join("\n", lines));
    }

    private ToolExecutionResult install(JsonNode parameters, CancellationToken cancellationToken) throws Exception {
        String source = parameters.path("source").asText("").trim();
        if (source.isEmpty()) {
            return ToolExecutionResult.text("错误：action=install 时 source 不能为空");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        String requestedSkillName = parameters.path("skill_name").asText("").trim();
        boolean overwrite = parameters.path("overwrite").asBoolean(false);
        String destinationRootRaw = parameters.path("destination_root").asText(DEFAULT_DEST_ROOT).trim();
        if (destinationRootRaw.isEmpty()) {
            destinationRootRaw = DEFAULT_DEST_ROOT;
        }
        Path destinationRoot = resolveWithinWorkspace(destinationRootRaw);
        if (destinationRoot == null) {
            return ToolExecutionResult.text("错误：destination_root 超出工作区范围");
        }

        Path tempDir = null;
        try {
            Path sourceRoot;
            if (looksLikeGitHub(source)) {
                tempDir = Files.createTempDirectory("java_agent_skill_install_");
                String repoSlug = normalizeGitHubSlug(source);
                String defaultBranch = fetchDefaultBranch(repoSlug);
                sourceRoot = cloneRepo(repoSlug, defaultBranch, tempDir);
            } else {
                Path local = resolveWithinWorkspace(source);
                if (local == null) {
                    return ToolExecutionResult.text("错误：本地 source 超出工作区范围");
                }
                sourceRoot = local;
            }

            if (!Files.isDirectory(sourceRoot)) {
                return ToolExecutionResult.text("错误：source 不是目录: " + sourceRoot);
            }

            List<Path> skillDirs = detectSkillDirectories(sourceRoot);
            if (skillDirs.isEmpty()) {
                return ToolExecutionResult.text("错误：未在 source 中找到 SKILL.md");
            }

            Path selected = selectSkillDirectory(skillDirs, requestedSkillName);
            if (selected == null) {
                List<String> names = new ArrayList<>();
                for (Path p : skillDirs) {
                    names.add(p.getFileName().toString());
                }
                return ToolExecutionResult.text("检测到多个 skill，请指定 skill_name: " + String.join(", ", names));
            }

            String directoryName = sanitizeDirectoryName(selected.getFileName().toString());
            if (directoryName.isBlank()) {
                return ToolExecutionResult.text("错误：skill 目录名非法");
            }

            Files.createDirectories(destinationRoot);
            Path targetDir = destinationRoot.resolve(directoryName).normalize();
            if (!targetDir.startsWith(destinationRoot)) {
                return ToolExecutionResult.text("错误：目标路径非法");
            }
            if (Files.exists(targetDir)) {
                if (!overwrite) {
                    return ToolExecutionResult.text("错误：目标 skill 已存在，若要覆盖请设置 overwrite=true: " + targetDir);
                }
                deleteRecursively(targetDir);
            }
            copyRecursively(selected, targetDir);

            String relTarget = workspaceRoot.relativize(targetDir).toString();
            return ToolExecutionResult.text(
                "安装成功: " + directoryName
                    + "\n路径: " + relTarget
                    + "\n可通过 skill(action=read, skill_name=\"" + directoryName + "\") 读取。"
            );
        } finally {
            if (tempDir != null) {
                try {
                    deleteRecursively(tempDir);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    private ToolExecutionResult readSkill(JsonNode parameters, CancellationToken cancellationToken) {
        String skillName = parameters.path("skill_name").asText("").trim();
        if (skillName.isEmpty()) {
            return ToolExecutionResult.text("错误：action=read 时 skill_name 不能为空");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        ClaudeSkillLoader.SkillLoadResult loadResult = skillLoader.loadFromProjectRoot(workspaceRoot);
        if (!loadResult.warnings().isEmpty()) {
            // warnings are non-fatal; continue to search best effort
        }

        ClaudeSkill matched = null;
        for (ClaudeSkill skill : loadResult.skills()) {
            String dirName = skill.getSourcePath().getParent().getFileName().toString();
            if (skill.getName().equalsIgnoreCase(skillName) || dirName.equalsIgnoreCase(skillName)) {
                matched = skill;
                break;
            }
        }

        if (matched == null) {
            List<String> names = new ArrayList<>();
            for (ClaudeSkill skill : loadResult.skills()) {
                names.add(skill.getName());
            }
            names.sort(String::compareToIgnoreCase);
            return ToolExecutionResult.text(
                "未找到 skill: " + skillName
                    + (names.isEmpty() ? "\n当前未发现任何已安装 skill。" : "\n当前可用 skill: " + String.join(", ", names))
            );
        }

        StringBuilder out = new StringBuilder();
        out.append("skill: ").append(matched.getName()).append("\n");
        if (!matched.getDescription().isBlank()) {
            out.append("description: ").append(matched.getDescription()).append("\n");
        }
        if (!matched.getFrontmatter().isEmpty()) {
            out.append("frontmatter:\n");
            for (Map.Entry<String, String> entry : matched.getFrontmatter().entrySet()) {
                out.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        out.append("\nSKILL.md content:\n");
        out.append(matched.getContent().strip());
        return ToolExecutionResult.text(out.toString().trim());
    }

    private ToolExecutionResult uninstall(JsonNode parameters, CancellationToken cancellationToken) throws IOException {
        String skillName = parameters.path("skill_name").asText("").trim();
        if (skillName.isEmpty()) {
            return ToolExecutionResult.text("错误：action=uninstall 时 skill_name 不能为空");
        }
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }

        Path skillsRoot = workspaceRoot.resolve(DEFAULT_DEST_ROOT).normalize();
        if (!skillsRoot.startsWith(workspaceRoot)) {
            return ToolExecutionResult.text("错误：skills 目录非法");
        }
        if (!Files.isDirectory(skillsRoot)) {
            return ToolExecutionResult.text("当前没有可卸载的 skill（未找到 .claude/skills 目录）。");
        }

        Path direct = skillsRoot.resolve(skillName).normalize();
        if (direct.startsWith(skillsRoot) && Files.isDirectory(direct)) {
            deleteRecursively(direct);
            return ToolExecutionResult.text("卸载成功: " + skillName);
        }

        ClaudeSkillLoader.SkillLoadResult loadResult = skillLoader.loadFromProjectRoot(workspaceRoot);
        for (ClaudeSkill skill : loadResult.skills()) {
            String name = skill.getName();
            String dirName = skill.getSourcePath().getParent().getFileName().toString();
            if (name.equalsIgnoreCase(skillName) || dirName.equalsIgnoreCase(skillName)) {
                Path target = skillsRoot.resolve(dirName).normalize();
                if (!target.startsWith(skillsRoot) || !Files.isDirectory(target)) {
                    return ToolExecutionResult.text("未找到可卸载目录: " + dirName);
                }
                deleteRecursively(target);
                return ToolExecutionResult.text("卸载成功: " + dirName);
            }
        }

        List<String> names = new ArrayList<>();
        for (ClaudeSkill skill : loadResult.skills()) {
            names.add(skill.getName());
        }
        names.sort(String::compareToIgnoreCase);
        return ToolExecutionResult.text(
            "未找到 skill: " + skillName
                + (names.isEmpty() ? "\n当前没有已安装 skill。" : "\n当前可用 skill: " + String.join(", ", names))
        );
    }

    private Path resolveWithinWorkspace(String raw) {
        Path input = Path.of(raw);
        Path resolved = input.isAbsolute()
            ? input.normalize()
            : workspaceRoot.resolve(input).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            return null;
        }
        return resolved;
    }

    private boolean looksLikeGitHub(String source) {
        return GITHUB_SLUG.matcher(source).matches() || GITHUB_URL.matcher(source).matches();
    }

    private String normalizeGitHubSlug(String source) {
        String raw = source.trim();
        if (GITHUB_SLUG.matcher(raw).matches()) {
            return raw;
        }
        Matcher matcher = GITHUB_URL.matcher(raw);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("不支持的 GitHub source: " + source);
        }
        return matcher.group(1);
    }

    private String fetchDefaultBranch(String slug) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + slug))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "java-agent-skill-tool")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub 仓库信息获取失败，status=" + response.statusCode());
        }
        JsonNode root = MAPPER.readTree(response.body());
        String branch = root.path("default_branch").asText("").trim();
        if (branch.isEmpty()) {
            throw new IOException("无法识别默认分支");
        }
        return branch;
    }

    private Path cloneRepo(String slug, String branch, Path tempDir) throws IOException, InterruptedException {
        Path checkoutDir = tempDir.resolve("repo");
        List<String> command = List.of(
            "git",
            "clone",
            "--depth",
            "1",
            "--branch",
            branch,
            "https://github.com/" + slug + ".git",
            checkoutDir.toString()
        );
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("git clone 失败: " + output.trim());
        }
        return checkoutDir;
    }

    private List<Path> detectSkillDirectories(Path sourceRoot) throws IOException {
        List<Path> skillDirs = new ArrayList<>();
        try (var walk = Files.walk(sourceRoot)) {
            walk.filter(path -> Files.isRegularFile(path) && "SKILL.md".equals(path.getFileName().toString()))
                .forEach(skillFile -> skillDirs.add(skillFile.getParent()));
        }
        skillDirs.sort(Comparator.comparing(Path::toString));
        return skillDirs;
    }

    private Path selectSkillDirectory(List<Path> skillDirs, String requestedSkillName) {
        if (skillDirs.isEmpty()) {
            return null;
        }
        if (requestedSkillName == null || requestedSkillName.isBlank()) {
            return skillDirs.size() == 1 ? skillDirs.get(0) : null;
        }
        for (Path skillDir : skillDirs) {
            String dirName = skillDir.getFileName().toString();
            if (dirName.equalsIgnoreCase(requestedSkillName.trim())) {
                return skillDir;
            }
        }
        return null;
    }

    private String sanitizeDirectoryName(String name) {
        String normalized = name.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        return normalized.replaceAll("^-+|-+$", "");
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                Path rel = source.relativize(src);
                Path dst = target.resolve(rel.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else if (Files.isRegularFile(src)) {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }
}
