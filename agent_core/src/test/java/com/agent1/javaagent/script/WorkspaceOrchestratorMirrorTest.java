package com.agent1.javaagent.script;

import com.agent1.javaagent.catalog.AgentCatalogPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceOrchestratorMirrorTest {

    @Test
    void prepare_mirrorsWorkspaceAndCatalogStdlib(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("docx.js"), "export const ok = true;\n");

        Files.createDirectories(workspace.resolve("jobs"));
        Files.writeString(
            workspace.resolve("jobs/run.js"),
            """
            import { ok } from './docx.js';
            export default ok;
            """.trimIndent(),
        );

        var layout = WorkspaceOrchestratorMirror.prepare(
            agentRoot,
            workspace,
            "jobs/run.js",
            Files.readString(workspace.resolve("jobs/run.js")),
        );
        assertTrue(layout.isPresent());
        Path mirroredEntry = layout.get().mirrorRoot().resolve("jobs/run.js");
        assertTrue(Files.isRegularFile(mirroredEntry));
        assertTrue(Files.isRegularFile(layout.get().mirrorRoot().resolve("jobs/docx.js")));
        assertTrue(layout.get().scriptFolderRelativeEntry().contains(".workspace-run/"));
        assertTrue(layout.get().scriptFolderRelativeEntry().endsWith("jobs/run.js"));

        WorkspaceOrchestratorMirror.deleteQuietly(layout.get().mirrorRoot());
    }

    @Test
    void prepare_followsWorkspaceLocalImport(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        Files.createDirectories(AgentCatalogPaths.catalogScriptsDir(agentRoot));
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("util.js"), "export function inc(x){ return x+1; }\n");
        Files.writeString(
            workspace.resolve("main.js"),
            "import { inc } from './util.js';\nexport default inc(1);\n",
        );

        var layout = WorkspaceOrchestratorMirror.prepare(
            agentRoot,
            workspace,
            "main.js",
            Files.readString(workspace.resolve("main.js")),
        );
        assertTrue(layout.isPresent());
        assertTrue(Files.isRegularFile(layout.get().mirrorRoot().resolve("util.js")));
        WorkspaceOrchestratorMirror.deleteQuietly(layout.get().mirrorRoot());
    }

    @Test
    void usesEsModules_detectsImportExport() {
        assertTrue(WorkspaceOrchestratorMirror.usesEsModules("import x from './a.js'"));
        assertTrue(WorkspaceOrchestratorMirror.usesEsModules("export default 1"));
        assertEquals(false, WorkspaceOrchestratorMirror.usesEsModules("JSON.stringify(1)"));
    }

    @Test
    void plan_catalogOnlyImports_skipMirror(@TempDir Path agentRoot, @TempDir Path workspace) throws Exception {
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("docx.js"), "export const ok = true;\n");
        Files.writeString(
            workspace.resolve("run.js"),
            "import { ok } from './docx.js';\nexport default ok;\n",
        );
        var plan = WorkspaceOrchestratorMirror.plan(
            agentRoot,
            workspace,
            "run.js",
            Files.readString(workspace.resolve("run.js")),
        );
        assertEquals(WorkspaceOrchestratorMirror.Strategy.EVAL_IN_SCRIPT_FOLDER, plan.strategy());
        assertTrue(plan.mirror().isEmpty());
    }

    @Test
    void rewriteBareCatalogImports(@TempDir Path agentRoot) throws Exception {
        Path scripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("docx.js"), "export {};\n");
        String out = WorkspaceOrchestratorMirror.rewriteBareCatalogImports(
            agentRoot,
            "import { markdownToDocx } from 'docx';\n",
        );
        assertTrue(out.contains("./docx.js"));
    }
}
