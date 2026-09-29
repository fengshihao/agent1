package com.agent1.javaagent.script;

import com.agent1.javaagent.catalog.AgentCatalogPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作区 orchestrator（execute_script file）在 QuickJS 下的模块解析。
 * <ul>
 *   <li>仅依赖 catalog 标准库 → {@link ExecutionPlan#evalInScriptFolder()}（无文件复制，等同 inline 模块上下文）</li>
 *   <li>含 workspace 本地 import → 镜像到 {@code .workspace-run/}（Weizhi 支持 workspace+catalog 回退后可删）</li>
 * </ul>
 */
public final class WorkspaceOrchestratorMirror {

    private static final String RUNS_DIR = ".workspace-run";
    private static final int KEEP_RUN_DIRS = 8;

    private static final Pattern RELATIVE_IMPORT = Pattern.compile(
        "import\\s+(?:[^'\"\\n]+?\\s+from\\s+)?['\"](\\.\\.?/[^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern BARE_IMPORT = Pattern.compile(
        "import\\s+(?:[^'\"\\n]+?\\s+from\\s+)?['\"]([^./][^'\"]*)['\"]",
        Pattern.MULTILINE
    );

    private WorkspaceOrchestratorMirror() {
    }

    public enum Strategy {
        /** 不镜像；QuickJS 文件名用 {@code null}（scriptFolder 上下文）。 */
        EVAL_IN_SCRIPT_FOLDER,
        /** 镜像到 .workspace-run。 */
        MIRROR,
    }

    public record ExecutionPlan(Strategy strategy, String effectiveSource, Optional<Layout> mirror) {
        static ExecutionPlan evalInScriptFolder(String source) {
            return new ExecutionPlan(Strategy.EVAL_IN_SCRIPT_FOLDER, source, Optional.empty());
        }

        static ExecutionPlan mirror(String source, Layout layout) {
            return new ExecutionPlan(Strategy.MIRROR, source, Optional.of(layout));
        }
    }

    public record Layout(Path mirrorRoot, String scriptFolderRelativeEntry) {
    }

    public static boolean usesEsModules(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        return source.contains("import ") || source.contains("export ");
    }

    /**
     * 决定如何执行 workspace 内 ES module 脚本；优先无复制的 scriptFolder 上下文。
     */
    public static ExecutionPlan plan(
        Path agentRoot,
        Path workspaceRoot,
        String workspaceRelativeEntry,
        String source
    ) throws IOException {
        if (agentRoot == null || workspaceRoot == null || !usesEsModules(source)) {
            return ExecutionPlan.evalInScriptFolder(source);
        }
        String rewritten = rewriteBareCatalogImports(agentRoot, source);
        DependencyGraph graph = analyzeDependencies(agentRoot, workspaceRoot, workspaceRelativeEntry, rewritten);
        if (!graph.needsWorkspaceMirror()) {
            return ExecutionPlan.evalInScriptFolder(rewritten);
        }
        Optional<Layout> layout = prepareMirror(agentRoot, workspaceRoot, workspaceRelativeEntry, graph);
        return ExecutionPlan.mirror(rewritten, layout.orElseThrow());
    }

    /**
     * @param workspaceRelativeEntry 工作区内入口脚本相对路径（与 execute_script file 一致）
     */
    public static Optional<Layout> prepare(
        Path agentRoot,
        Path workspaceRoot,
        String workspaceRelativeEntry,
        String source
    ) throws IOException {
        if (agentRoot == null || workspaceRoot == null || !usesEsModules(source)) {
            return Optional.empty();
        }
        DependencyGraph graph = analyzeDependencies(agentRoot, workspaceRoot, workspaceRelativeEntry, source);
        return prepareMirror(agentRoot, workspaceRoot, workspaceRelativeEntry, graph);
    }

    private record DependencyGraph(Map<Path, Path> mirrorToSource, boolean needsWorkspaceMirror) {
    }

    private static Optional<Layout> prepareMirror(
        Path agentRoot,
        Path workspaceRoot,
        String workspaceRelativeEntry,
        DependencyGraph graph
    ) throws IOException {
        Path scriptsDir = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.createDirectories(scriptsDir);
        Path entryRel = normalizeWorkspaceRelative(workspaceRelativeEntry);

        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path mirrorRoot = scriptsDir.resolve(RUNS_DIR).resolve(runId);
        for (Map.Entry<Path, Path> e : graph.mirrorToSource().entrySet()) {
            Path target = mirrorRoot.resolve(e.getKey());
            Files.createDirectories(target.getParent() == null ? mirrorRoot : target.getParent());
            Files.copy(e.getValue(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        pruneOldRuns(scriptsDir.resolve(RUNS_DIR));
        String scriptFolderEntry = RUNS_DIR + "/" + runId + "/" + entryRel.toString().replace('\\', '/');
        return Optional.of(new Layout(mirrorRoot, scriptFolderEntry));
    }

    private static DependencyGraph analyzeDependencies(
        Path agentRoot,
        Path workspaceRoot,
        String workspaceRelativeEntry,
        String source
    ) throws IOException {
        Path scriptsDir = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Path entryRel = normalizeWorkspaceRelative(workspaceRelativeEntry);
        Path entryAbs = workspaceRoot.resolve(entryRel).normalize();
        if (!entryAbs.startsWith(workspaceRoot.normalize()) || !Files.isRegularFile(entryAbs)) {
            return new DependencyGraph(Map.of(), false);
        }

        Map<Path, Path> mirrorToSource = new LinkedHashMap<>();
        boolean needsWorkspaceMirror = false;
        ArrayDeque<Path> queue = new ArrayDeque<>();
        Set<Path> seen = new HashSet<>();
        queue.add(entryRel);
        seen.add(entryRel);

        while (!queue.isEmpty()) {
            Path rel = queue.removeFirst();
            Path abs = mirrorToSource.get(rel);
            if (abs == null) {
                abs = resolveReadableFile(workspaceRoot, scriptsDir, rel);
                if (abs == null || !Files.isRegularFile(abs)) {
                    throw new IOException("无法解析脚本依赖: " + rel);
                }
                mirrorToSource.put(rel, abs);
            }
            String text = rel.equals(entryRel) ? source : Files.readString(abs);
            Matcher m = RELATIVE_IMPORT.matcher(text);
            while (m.find()) {
                Path depRel = resolveImportRelative(rel.getParent(), m.group(1).trim());
                if (depRel == null) {
                    continue;
                }
                Path depWs = workspaceRoot.resolve(depRel).normalize();
                if (depWs.startsWith(workspaceRoot.normalize()) && Files.isRegularFile(depWs)) {
                    needsWorkspaceMirror = true;
                }
                if (!mirrorToSource.containsKey(depRel)) {
                    Path depAbs = resolveReadableFile(workspaceRoot, scriptsDir, depRel);
                    if (depAbs == null) {
                        throw new IOException("无法解析 import '" + m.group(1) + "'（自 " + rel + "）");
                    }
                    mirrorToSource.put(depRel, depAbs);
                }
                if (seen.add(depRel)) {
                    queue.add(depRel);
                }
            }
        }
        return new DependencyGraph(mirrorToSource, needsWorkspaceMirror);
    }

    /** bare name（如 docx、docx.js）→ ./docx.js，仅当 catalog 根目录存在该文件。 */
    static String rewriteBareCatalogImports(Path agentRoot, String source) throws IOException {
        Path scriptsDir = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Matcher m = BARE_IMPORT.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String spec = m.group(1).trim();
            if (spec.isEmpty() || spec.startsWith("http://") || spec.startsWith("https://")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String leaf = spec.endsWith(".js") ? spec : spec + ".js";
            Path cat = scriptsDir.resolve(leaf);
            if (!Files.isRegularFile(cat)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String replacement = m.group(0).replace(spec, "./" + leaf);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void deleteQuietly(Path mirrorRoot) {
        if (mirrorRoot == null) {
            return;
        }
        try {
            if (!Files.isDirectory(mirrorRoot)) {
                return;
            }
            try (var walk = Files.walk(mirrorRoot)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
            Path runParent = mirrorRoot.getParent();
            if (runParent != null && runParent.getFileName() != null
                && RUNS_DIR.equals(runParent.getParent() == null ? null : runParent.getParent().getFileName().toString())) {
                // leave RUNS_DIR; only removed run id folder above
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static Path resolveReadableFile(
        Path workspaceRoot,
        Path catalogScripts,
        Path workspaceRelative
    ) {
        Path ws = workspaceRoot.resolve(workspaceRelative).normalize();
        if (ws.startsWith(workspaceRoot.normalize()) && Files.isRegularFile(ws)) {
            return ws;
        }
        if (workspaceRelative.getNameCount() == 1) {
            Path cat = catalogScripts.resolve(workspaceRelative.getFileName());
            if (Files.isRegularFile(cat)) {
                return cat;
            }
        }
        Path catLeaf = catalogScripts.resolve(workspaceRelative.getFileName());
        if (Files.isRegularFile(catLeaf)) {
            return catLeaf;
        }
        return null;
    }

    private static Path resolveImportRelative(Path fromDir, String importSpec) {
        if (importSpec == null || importSpec.isBlank()) {
            return null;
        }
        Path base = fromDir == null ? Path.of("") : fromDir;
        Path resolved;
        if (importSpec.startsWith("./")) {
            resolved = base.resolve(importSpec.substring(2)).normalize();
        } else if (importSpec.startsWith("../")) {
            resolved = base.resolve(importSpec).normalize();
        } else {
            return null;
        }
        if (resolved.isAbsolute() || resolved.startsWith("..")) {
            return null;
        }
        return resolved;
    }

    private static Path normalizeWorkspaceRelative(String workspaceRelativeEntry) {
        String normalized = workspaceRelativeEntry.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path p = Path.of(normalized).normalize();
        if (p.startsWith("..")) {
            throw new IllegalArgumentException("path outside workspace: " + workspaceRelativeEntry);
        }
        return p;
    }

    private static void pruneOldRuns(Path runsRoot) throws IOException {
        if (!Files.isDirectory(runsRoot)) {
            return;
        }
        try (var dirs = Files.list(runsRoot)
            .filter(Files::isDirectory)
            .sorted((a, b) -> Long.compare(
                lastModifiedSafe(b),
                lastModifiedSafe(a)
            ))) {
            var list = dirs.toList();
            for (int i = KEEP_RUN_DIRS; i < list.size(); i++) {
                deleteQuietly(list.get(i));
            }
        }
    }

    private static long lastModifiedSafe(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
