package com.agent1.javaagent.session;

import com.agent1.javaagent.model.AgentMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 磁盘布局：{@code <agentRoot>/sessions/<sessionId>/meta.json}、{@code transcript.jsonl}、
 * {@code workspace/artifacts/}、{@code workspace/.spill/}。
 */
public final class FileSessionStore implements SessionStore {

    private static final String DEFAULT_TITLE = "新对话";
    private static final int AUTO_TITLE_CHARS = 24;

    private final Path agentRoot;
    private final ObjectMapper mapper;
    private final TranscriptCodec codec;

    public FileSessionStore(Path agentRoot) {
        this(agentRoot, new ObjectMapper());
    }

    public FileSessionStore(Path agentRoot, ObjectMapper mapper) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.mapper = mapper;
        this.codec = new TranscriptCodec(mapper);
    }

    public Path sessionsRoot() {
        return agentRoot.resolve("sessions");
    }

    public Path sessionDir(String sessionId) {
        return sessionsRoot().resolve(sessionId);
    }

    public Path workspaceDir(String sessionId) {
        return sessionDir(sessionId).resolve("workspace");
    }

    @Override
    public List<SessionMeta> listSessions() {
        Path root = sessionsRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SessionMeta> out = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                Path metaFile = dir.resolve("meta.json");
                if (!Files.isRegularFile(metaFile)) {
                    continue;
                }
                try {
                    out.add(readMeta(metaFile));
                } catch (IOException ignored) {
                    // 跳过损坏目录
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("list sessions failed: " + root, e);
        }
        out.sort(Comparator.comparing(SessionMeta::getUpdatedAt).reversed());
        return out;
    }

    @Override
    public SessionMeta createSession() {
        String sessionId = UUID.randomUUID().toString();
        Path dir = sessionDir(sessionId);
        try {
            Files.createDirectories(dir.resolve("workspace").resolve("artifacts"));
            Files.createDirectories(dir.resolve("workspace").resolve(".spill"));
        } catch (IOException e) {
            throw new IllegalStateException("create session dirs failed: " + dir, e);
        }
        String now = Instant.now().toString();
        SessionMeta meta = new SessionMeta(sessionId, DEFAULT_TITLE, now, now);
        writeMeta(dir, meta);
        try {
            Files.createFile(dir.resolve("transcript.jsonl"));
        } catch (IOException e) {
            throw new IllegalStateException("create transcript failed: " + dir, e);
        }
        return meta;
    }

    @Override
    public SessionMeta getSession(String sessionId) {
        try {
            return readMeta(metaFile(requireSession(sessionId)));
        } catch (IOException e) {
            throw new IllegalStateException("read session meta failed: " + sessionId, e);
        }
    }

    @Override
    public SessionMeta renameSession(String sessionId, String title) {
        Path dir = requireSession(sessionId);
        SessionMeta meta;
        try {
            meta = readMeta(metaFile(dir));
        } catch (IOException e) {
            throw new IllegalStateException("read session meta failed: " + sessionId, e);
        }
        SessionMeta updated = meta.withTitle(title == null ? "" : title.trim())
            .withUpdatedAt(Instant.now().toString());
        writeMeta(dir, updated);
        return updated;
    }

    @Override
    public void deleteSession(String sessionId) {
        Path dir = sessionDir(sessionId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        deleteRecursive(dir);
    }

    @Override
    public List<AgentMessage> loadTranscript(String sessionId) {
        Path file = requireSession(sessionId).resolve("transcript.jsonl");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<AgentMessage> messages = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                messages.add(codec.fromLine(line));
            }
        } catch (IOException e) {
            throw new IllegalStateException("read transcript failed: " + file, e);
        }
        return messages;
    }

    @Override
    public synchronized void appendMessage(String sessionId, String runId, AgentMessage message) {
        Path dir = requireSession(sessionId);
        Path transcript = dir.resolve("transcript.jsonl");
        String line = codec.toLine(runId, message);
        try {
            Files.writeString(
                transcript,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("append transcript failed: " + transcript, e);
        }
        SessionMeta meta;
        try {
            meta = readMeta(metaFile(dir));
        } catch (IOException e) {
            throw new IllegalStateException("read session meta failed: " + sessionId, e);
        }
        String now = Instant.now().toString();
        SessionMeta updated = meta.withUpdatedAt(now);
        if (DEFAULT_TITLE.equals(meta.getTitle())
            && AgentMessage.ROLE_USER.equals(message.getRole())
            && !message.getContent().isBlank()) {
            updated = updated.withTitle(truncateTitle(message.getContent()));
        }
        writeMeta(dir, updated);
    }

    private Path requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId required");
        }
        Path dir = sessionDir(sessionId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("session not found: " + sessionId);
        }
        return dir;
    }

    private Path metaFile(Path sessionDir) {
        return sessionDir.resolve("meta.json");
    }

    private SessionMeta readMeta(Path metaFile) throws IOException {
        JsonNode root = mapper.readTree(Files.readString(metaFile, StandardCharsets.UTF_8));
        return new SessionMeta(
            root.path("sessionId").asText(""),
            root.path("title").asText(""),
            root.path("createdAt").asText(""),
            root.path("updatedAt").asText("")
        );
    }

    private void writeMeta(Path sessionDir, SessionMeta meta) {
        ObjectNode root = mapper.createObjectNode();
        root.put("sessionId", meta.getSessionId());
        root.put("title", meta.getTitle());
        root.put("createdAt", meta.getCreatedAt());
        root.put("updatedAt", meta.getUpdatedAt());
        Path file = metaFile(sessionDir);
        Path tmp = sessionDir.resolve("meta.json.tmp");
        try {
            Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("write meta failed: " + file, e);
        }
    }

    private static String truncateTitle(String text) {
        String t = text.strip();
        if (t.length() <= AUTO_TITLE_CHARS) {
            return t;
        }
        return t.substring(0, AUTO_TITLE_CHARS);
    }

    private static void deleteRecursive(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new IllegalStateException("delete session failed: " + p, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("delete session walk failed: " + root, e);
        }
    }
}
