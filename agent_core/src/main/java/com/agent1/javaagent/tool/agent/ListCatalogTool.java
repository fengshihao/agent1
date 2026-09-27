package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.agent.AgentReadScope;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** shared/catalog 按类别文件数量摘要（REQ-012，非全量递归 listing）。 */
public final class ListCatalogTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] CATALOG_KINDS = {
        "skills",
        "scripts",
        "libs/js",
        "libs/qjs",
        "assets/images",
        "assets/data",
        "native",
        "bundles"
    };

    private final Path agentRoot;

    public ListCatalogTool(Path agentRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "list_catalog";
    }

    @Override
    public String description() {
        return "Summarize file counts under agentRoot shared/catalog by kind (read-only index).";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        schema.set("properties", properties);
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
        Path catalogRoot = AgentReadScope.resolveCatalogRoot(agentRoot);
        StringBuilder out = new StringBuilder();
        out.append("CATALOG_ROOT: shared/catalog\n");
        if (!Files.isDirectory(catalogRoot)) {
            out.append("(empty — catalog directory missing)\n");
            return ToolExecutionResult.text(out.toString().trim());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String kind : CATALOG_KINDS) {
            Path dir = catalogRoot.resolve(kind);
            counts.put(kind, countRegularFiles(dir, 4));
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" files\n");
            total += entry.getValue();
        }
        out.append("TOTAL catalog: ").append(total).append(" files (max depth 4 per kind)\n");
        Path localRoot = agentRoot.resolve("shared/local");
        int localSkills = countRegularFiles(localRoot.resolve("skills"), 4);
        int localScripts = countRegularFiles(localRoot.resolve("scripts"), 2);
        out.append("LOCAL promoted: skills=").append(localSkills).append(" scripts=").append(localScripts);
        return ToolExecutionResult.text(out.toString().trim());
    }

    private static int countRegularFiles(Path dir, int maxDepth) {
        if (!Files.isDirectory(dir) || maxDepth < 0) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> walk = Files.walk(dir, maxDepth)) {
            count = (int) walk.filter(Files::isRegularFile).count();
        } catch (IOException ignored) {
            return 0;
        }
        return count;
    }
}
