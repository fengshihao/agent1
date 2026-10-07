package com.agent1.javaagent.todo;

import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** {@code sessions/<id>/todos.json}。空清单删除文件。 */
public final class TodoStore {

    public static final String FILE_NAME = "todos.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path sessionDir;

    public TodoStore(Path sessionDir) {
        if (sessionDir == null) {
            throw new IllegalArgumentException("sessionDir required");
        }
        this.sessionDir = sessionDir.toAbsolutePath().normalize();
    }

    public Path file() {
        return sessionDir.resolve(FILE_NAME);
    }

    public synchronized LoadResult load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return LoadResult.ok(TodoList.empty());
        }
        try {
            JsonNode root = MAPPER.readTree(PathIo.readString(path, StandardCharsets.UTF_8));
            TodoList.ParseResult parsed = TodoList.parse(root.get("todos"));
            if (!parsed.ok()) {
                return LoadResult.unreadable(parsed.error());
            }
            return LoadResult.ok(parsed.list());
        } catch (IOException e) {
            return LoadResult.unreadable("错误：无法读取 todos.json");
        }
    }

    /** 空清单删除文件。调用方负责「与当前相同则不写」。 */
    public synchronized void save(TodoList list) {
        if (list == null) {
            throw new IllegalArgumentException("list required");
        }
        Path path = file();
        if (list.isEmpty()) {
            try {
                Files.deleteIfExists(path);
                Files.deleteIfExists(sessionDir.resolve(FILE_NAME + ".tmp"));
            } catch (IOException e) {
                throw new IllegalStateException("delete todos failed: " + path, e);
            }
            return;
        }
        if (!Files.isDirectory(sessionDir)) {
            throw new IllegalStateException("session dir missing: " + sessionDir);
        }
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode todos = root.putArray("todos");
        for (TodoList.Item item : list.items()) {
            ObjectNode node = todos.addObject();
            node.put("id", item.id());
            node.put("content", item.content());
            node.put("status", item.status());
        }
        Path tmp = sessionDir.resolve(FILE_NAME + ".tmp");
        try {
            PathIo.writeString(
                tmp,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8
            );
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("write todos failed: " + path, e);
        }
    }

    public record LoadResult(TodoList list, String error) {
        static LoadResult ok(TodoList list) {
            return new LoadResult(list, null);
        }

        static LoadResult unreadable(String error) {
            return new LoadResult(TodoList.empty(), error);
        }

        public boolean ok() {
            return error == null;
        }
    }
}
