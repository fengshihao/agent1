package com.agent1.javaagent.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * Android 兼容的文本读写：避免 {@code Files.readString}/{@code writeString}
 *（API 24–33 上 java.nio.file.Files 无此方法，desugar 也不覆盖）。
 */
public final class PathIo {

    private PathIo() {
    }

    public static String readString(Path path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public static String readString(Path path, Charset charset) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, charset);
    }

    public static void writeString(Path path, CharSequence content, OpenOption... options)
        throws IOException {
        writeString(path, content, StandardCharsets.UTF_8, options);
    }

    public static void writeString(
        Path path,
        CharSequence content,
        Charset charset,
        OpenOption... options
    ) throws IOException {
        byte[] bytes = content.toString().getBytes(charset);
        if (options == null || options.length == 0) {
            Files.write(path, bytes);
        } else {
            Files.write(path, bytes, options);
        }
    }
}
