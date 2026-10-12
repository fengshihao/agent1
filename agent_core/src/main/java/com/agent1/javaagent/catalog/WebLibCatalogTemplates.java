package com.agent1.javaagent.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 把预置 HTML 薄壳装进 {@code shared/catalog/templates}。
 * 真源是 classpath {@code agent-home/catalog/templates}。find_caps 的 web_lib 条目内嵌同一份壳。
 */
public final class WebLibCatalogTemplates {

    private static final String INDEX = "/agent-home/catalog/templates/index.txt";

    private WebLibCatalogTemplates() {
    }

    public static void ensure(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        Path dir = agentRoot.toAbsolutePath().normalize().resolve("shared/catalog/templates");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("create web lib templates dir failed: " + dir, e);
        }
        String index = readIndex();
        if (index.isEmpty()) {
            return;
        }
        for (String line : index.split("\n")) {
            String name = line.trim();
            if (name.isEmpty() || name.startsWith("#")) {
                continue;
            }
            copy(name, dir.resolve(name));
        }
    }

    private static String readIndex() {
        // null 检查放在 try-with-resources 之外，避免隐式 close 读取已知 null 的资源句柄
        InputStream source = WebLibCatalogTemplates.class.getResourceAsStream(INDEX); // NOPMD CloseResource：source 是下一行 try-with-resources 的资源，块结束即关闭
        if (source == null) {
            return "";
        }
        try (InputStream in = source) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read web lib template index failed", e);
        }
    }

    private static void copy(String name, Path target) {
        String resource = "/agent-home/catalog/templates/" + name;
        InputStream source = WebLibCatalogTemplates.class.getResourceAsStream(resource); // NOPMD CloseResource：source 是下一行 try-with-resources 的资源，块结束即关闭
        if (source == null) {
            return;
        }
        try (InputStream in = source) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("copy web lib template failed: " + target, e);
        }
    }
}
