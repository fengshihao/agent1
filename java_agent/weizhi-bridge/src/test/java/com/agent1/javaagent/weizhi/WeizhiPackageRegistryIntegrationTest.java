package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.capability.CapabilityIndexStore;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.script.ExecuteScriptTool;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.weizhi.platform.MiniJson;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeizhiPackageRegistryIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void ensureIndexedAndRunJsEnsure() throws Exception {
        byte[] zip = packageZip("leaf", "1.0.0", "export function ping(){ return 42; }\n");
        try (LocalRegistry registry = new LocalRegistry(zip)) {
            Path agentRoot = temp.resolve("agent");
            writeAgentManifest(agentRoot, registry.registryUrl());
            WeizhiPackageRegistrySync.SyncResult sync = WeizhiPackageRegistrySync.ensureIndexed(agentRoot);
            assertTrue(sync.indexed());
            assertEquals(1, sync.packageCount());

            var hits = CapabilityIndexStore.search(agentRoot, "leaf ping", List.of("weizhi_package"), "any", 5);
            assertFalse(hits.isEmpty());
            assertTrue(hits.get(0).id().contains("leaf"));

            Path workspace = temp.resolve("ws");
            Files.createDirectories(workspace);
            ScriptEngineFactory factory = ws -> new ScriptEngine() {
                @Override
                public String eval(
                    String jsSource,
                    long timeoutMs,
                    CancellationToken token
                ) {
                    return "\"ok\"";
                }

                @Override
                public void close() {}
            };
            ExecuteScriptTool tool = new ExecuteScriptTool(
                new WorkspaceSandbox(workspace),
                factory,
                60_000,
                agentRoot,
                WeizhiScriptPackageEnsurer.optional(agentRoot)
            );
            String code = "import { ping } from 'weizhi:leaf';\nexport default ping();\n";
            ObjectNode params = MAPPER.createObjectNode().put("code", code);
            var result = tool.execute("t1", params, new CancellationToken(), u -> {});
            assertTrue(result.getText().contains("ok"), result.getText());

            Path home = WeizhiBundledPackages.weizhiHome(agentRoot);
            assertTrue(Files.isDirectory(home.resolve("packages").resolve("leaf@1.0.0")));
        }
    }

    private static void writeAgentManifest(Path agentRoot, String registryUrl) throws IOException {
        Files.createDirectories(agentRoot);
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode wp = root.putObject("weizhiPackage");
        wp.put("registryUrl", registryUrl);
        ArrayNode hosts = wp.putArray("allowedHosts");
        hosts.add("127.0.0.1");
        Files.writeString(
            agentRoot.resolve("agent.manifest.json"),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
            StandardCharsets.UTF_8
        );
    }

    private static byte[] packageZip(String name, String version, String js) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            String manifest =
                "{\"name\":\"" + name + "\",\"version\":\"" + version + "\",\"entry\":\"index.js\"}\n";
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("index.js"));
            zos.write(js.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static final class LocalRegistry implements AutoCloseable {
        final HttpServer server;
        final int port;
        final byte[] artifact;

        LocalRegistry(byte[] artifact) throws IOException {
            this.artifact = artifact;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            port = server.getAddress().getPort();
            server.createContext("/registry/stable.json", this::serveManifest);
            server.createContext("/artifacts/leaf.zip", this::serveArtifact);
            server.start();
        }

        String registryUrl() {
            return "http://127.0.0.1:" + port + "/registry/stable.json";
        }

        String artifactUrl() {
            return "http://127.0.0.1:" + port + "/artifacts/leaf.zip";
        }

        private void serveManifest(HttpExchange exchange) throws IOException {
            String body;
            try {
                body = manifestJson(artifactUrl(), sha256(artifact), artifact.length);
            } catch (Exception e) {
                throw new IOException(e);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private void serveArtifact(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, artifact.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(artifact);
            }
        }

        private static String manifestJson(String artifactUrl, String sha, long size) {
            return "{"
                + "\"schema_version\":1,"
                + "\"channel\":\"stable\","
                + "\"packages\":[{"
                + "\"name\":\"leaf\","
                + "\"version\":\"1.0.0\","
                + "\"description\":\"demo\","
                + "\"min_engine\":\"0.1.0\","
                + "\"artifact\":{"
                + "\"url\":" + MiniJson.quote(artifactUrl) + ","
                + "\"sha256\":" + MiniJson.quote(sha) + ","
                + "\"size\":" + size
                + "},"
                + "\"capability\":{"
                + "\"title\":\"Leaf\","
                + "\"summary\":\"ping\","
                + "\"entry\":" + MiniJson.quote("import { ping } from \"weizhi:leaf\";")
                + "}}]}";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
