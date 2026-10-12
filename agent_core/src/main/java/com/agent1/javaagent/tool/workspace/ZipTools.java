package com.agent1.javaagent.tool.workspace;

import com.agent1.javaagent.tool.anno.Tool;
import com.agent1.javaagent.tool.anno.ToolParam;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 工作区内打包与解压。只读区里的 zip 会先复制进工作区再解。 */
public final class ZipTools {

    private static final int MAX_ENTRIES = 10_000;

    private final WorkspaceSandbox sandbox;

    public ZipTools(WorkspaceSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Tool(
        name = "zip_extract",
        description = "解压 zip 到工作区目录（默认 tmp/<zip名>/）。",
        readOnly = false,
        concurrencySafe = false
    )
    public String zipExtract(
        @ToolParam(name = "file", description = "zip 路径（工作区相对或只读区绝对）") String file,
        @ToolParam(name = "dest", required = false, description = "目标目录（工作区内相对）") String dest
    ) {
        try {
            Path zipPath = sandbox.resolveRead(file);
            Path root = sandbox.getRoot();
            if (!zipPath.startsWith(root)) {
                Path zipName = zipPath.getFileName();
                if (zipName == null) {
                    return "Error: bad zip path";
                }
                Path staging = root.resolve("tmp/_zip_import").resolve(zipName).normalize();
                if (!staging.startsWith(root)) {
                    return "Error: path escape";
                }
                Path parent = staging.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(zipPath, staging, StandardCopyOption.REPLACE_EXISTING);
                zipPath = staging;
            }
            // 注意：这里必须直接用已解析的 Path，不能再经 relativize 转成展示路径后重新
            // resolveRead——展示路径是 agentRoot 相对（sessions/<id>/workspace/...），
            // 回转后会拼到 workspace/sessions/... 下导致「zip file not found」。
            return extractAt(zipPath, dest, file);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
        name = "zip_create",
        description = "将工作区目录打包为 zip。",
        readOnly = false,
        concurrencySafe = false
    )
    public String zipCreate(
        @ToolParam(name = "sourceDir", description = "源目录（工作区内相对）") String sourceDir,
        @ToolParam(name = "file", description = "输出 zip（工作区内相对）") String file
    ) {
        try {
            sandbox.resolveWrite(sourceDir);
            sandbox.resolveWrite(file);
            return createRelative(sourceDir, file);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String extractAt(Path zip, String dest, String displayInput) throws IOException {
        if (!Files.isRegularFile(zip)) {
            return "Error: zip file not found: " + displayInput;
        }
        Path zipName = zip.getFileName();
        String destDir = dest != null && !dest.isBlank()
            ? dest.trim()
            : "tmp/" + stripExtension(zipName == null ? "unpacked" : zipName.toString());
        Path outDir = sandbox.resolveWrite(destDir);
        int entries = 0;
        int skipped = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entries >= MAX_ENTRIES) {
                    return "Error: too many zip entries (limit " + MAX_ENTRIES + "), aborted at "
                        + sandbox.relativize(outDir);
                }
                String entryName = entry.getName().replace('\\', '/');
                if (entryName.startsWith("/") || entryName.equals("..")
                    || entryName.startsWith("../") || entryName.contains("/../")) {
                    skipped++;
                    continue;
                }
                Path target;
                try {
                    String joined = sandbox.relativize(outDir);
                    joined = ".".equals(joined) ? entryName : joined + "/" + entryName;
                    target = sandbox.resolveWrite(joined);
                } catch (SecurityException e) {
                    skipped++;
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                entries++;
            }
        } catch (IOException e) {
            return "Error: unzip failed: " + e.getMessage() + " (extracted " + entries
                + " entries to " + sandbox.relativize(outDir) + ")";
        }
        String msg = "Extracted " + entries + " entries to " + sandbox.relativize(outDir);
        return skipped > 0 ? msg + " (skipped " + skipped + " unsafe entries)" : msg;
    }

    private String createRelative(String sourceDir, String file) {
        if (sourceDir == null || sourceDir.isBlank() || file == null || file.isBlank()) {
            return "Error: source_dir and file are required";
        }
        Path src = sandbox.resolveWrite(sourceDir);
        Path out = sandbox.resolveWrite(file);
        if (!Files.isDirectory(src)) {
            return "Error: source dir not found: " + sourceDir;
        }
        Path outParent = out.getParent();
        if (outParent != null) {
            try {
                Files.createDirectories(outParent);
            } catch (IOException e) {
                return "Error: create output dir failed: " + e.getMessage();
            }
        }
        int[] files = {0};
        try (Stream<Path> walk = Files.walk(src);
            OutputStream os = Files.newOutputStream(out);
            ZipOutputStream zos = new ZipOutputStream(os)) {
            walk.filter(Files::isRegularFile)
                .filter(path -> !path.equals(out))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
                    try {
                        String name = src.relativize(path).toString().replace('\\', '/');
                        zos.putNextEntry(new ZipEntry(name));
                        Files.copy(path, zos);
                        zos.closeEntry();
                        files[0]++;
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            zos.finish();
        } catch (IOException | IllegalStateException e) {
            return "Error: zip failed: " + e.getMessage();
        }
        return "Created " + sandbox.relativize(out) + " with " + files[0] + " files from "
            + sandbox.relativize(src);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
