package com.agent1.javaagent.tool.script;

import com.agent1.javaagent.script.ScriptEvalFrame;
import com.agent1.javaagent.util.PathIo;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** inline {@code code} 超过阈值时落到 workspace {@code jobs/}，再按 file 执行。 */
public final class InlineScriptSpill {

    public static final int MAX_LINES = 20;
    public static final int MAX_CHARS = 1000;
    public static final String MARKER = "[script] inline 过长，已写入 ";

    private InlineScriptSpill() {
    }

    static boolean exceeds(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return ScriptEvalFrame.countLines(code) > MAX_LINES || code.length() > MAX_CHARS;
    }

    static String write(WorkspaceSandbox sandbox, String code) throws IOException {
        String digest = digest8(code);
        String relative = "jobs/inline-" + digest + ".js";
        for (int n = 0; n < 100; n++) {
            if (n > 0) {
                relative = "jobs/inline-" + digest + "-" + n + ".js";
            }
            Path path = sandbox.resolveWrite(relative);
            if (Files.isRegularFile(path)) {
                String existing = PathIo.readString(path);
                if (code.equals(existing)) {
                    return relative;
                }
                continue;
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            PathIo.writeString(path, code, StandardCharsets.UTF_8);
            return relative;
        }
        throw new IOException("jobs/ 下无法为 inline 脚本分配文件名");
    }

    public static String notice(String relativePath) {
        return "\n\n---\n" + MARKER + relativePath
            + " 并执行。后续改动请 edit_file 该文件，再用 execute_script 的 file 参数。";
    }

    private static String digest8(String code) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(code.hashCode());
        }
    }
}
