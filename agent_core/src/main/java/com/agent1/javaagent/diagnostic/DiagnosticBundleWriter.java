package com.agent1.javaagent.diagnostic;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 把目录与文本打进诊断 zip（桌面与 Android 共用逻辑）。 */
public final class DiagnosticBundleWriter {

    public static final long DEFAULT_MAX_FILE_BYTES = 8L * 1024 * 1024;

    public record NamedText(String entryName, String text) {
    }

    public record NamedDir(String entryPrefix, Path dir) {
    }

    public record Request(Path outputZip, List<NamedDir> trees, List<NamedText> texts, long maxFileBytes) {
        public Request(Path outputZip, List<NamedDir> trees, List<NamedText> texts) {
            this(outputZip, trees, texts, DEFAULT_MAX_FILE_BYTES);
        }
    }

    public record Written(Path zip, int includedFiles, List<String> skipped) {
    }

    public Written write(Request request) throws IOException {
        Path parent = request.outputZip().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> skipped = new ArrayList<>();
        int included = 0;
        Path zipPath = request.outputZip().toAbsolutePath().normalize();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
            Files.newOutputStream(request.outputZip())
        ))) {
            for (NamedText text : request.texts()) {
                String name = normalizeEntry(text.entryName());
                zip.putNextEntry(new ZipEntry(name));
                zip.write(text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
                included++;
            }
            for (NamedDir tree : request.trees()) {
                Path dir = tree.dir();
                if (!Files.isDirectory(dir)) {
                    skipped.add("缺少目录 " + dir);
                    continue;
                }
                String prefix = normalizeEntry(tree.entryPrefix()).replaceAll("/+$", "");
                included += addTree(zip, dir, dir, prefix, zipPath, request.maxFileBytes(), skipped);
            }
            if (!skipped.isEmpty()) {
                StringBuilder note = new StringBuilder();
                note.append("以下文件未打入包（缺失或超过 ").append(request.maxFileBytes()).append(" 字节）：\n");
                for (String line : skipped) {
                    note.append("- ").append(line).append('\n');
                }
                zip.putNextEntry(new ZipEntry("skipped.txt"));
                zip.write(note.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new Written(request.outputZip(), included, skipped);
    }

    private static int addTree(
        ZipOutputStream zip,
        Path root,
        Path dir,
        String prefix,
        Path outputZip,
        long maxBytes,
        List<String> skipped
    ) throws IOException {
        int[] count = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toAbsolutePath().normalize().equals(outputZip)) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = root.relativize(file);
                String relativePath = relative.toString().replace('\\', '/');
                String entryName = prefix.isEmpty() ? relativePath : prefix + "/" + relativePath;
                entryName = normalizeEntry(entryName);
                long size = attrs.size();
                if (size > maxBytes) {
                    skipped.add(entryName + " (" + size + " bytes)");
                    return FileVisitResult.CONTINUE;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static String normalizeEntry(String name) {
        return name.replace('\\', '/').replaceAll("^/+", "");
    }
}
