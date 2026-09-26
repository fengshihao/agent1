package com.agent1.javaagent.weizhi.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 从 weizhi 仓库读取 bootstrap.html + bridge.js，并注入 CDP 用 {@code NativeBridge} 垫片。 */
public final class DesktopBootstrapAssets {

    static final String PLACEHOLDER = "/*__BRIDGE_JS__*/";
    private static final String CDP_NATIVE_BRIDGE_SHIM = """
        window.__agentBindingQueue = window.__agentBindingQueue || [];
        function __agentEmit(msg) {
          window.__agentBindingQueue.push(msg);
          if (typeof agentNativeBridge === 'function') {
            agentNativeBridge(msg);
          }
        }
        window.NativeBridge = {
          onResult: function (taskId, payload, isError) {
            __agentEmit(JSON.stringify({
              k: 'r', taskId: taskId, payload: payload, isError: isError
            }));
          },
          onChunk: function (taskId, seq, total, b64) {
            __agentEmit(JSON.stringify({
              k: 'c', taskId: taskId, seq: seq, total: total, b64: b64
            }));
          }
        };
        """;

    private final Path weizhiRepo;
    private volatile String cachedHtml;

    public DesktopBootstrapAssets(Path weizhiRepo) {
        this.weizhiRepo = weizhiRepo.toAbsolutePath().normalize();
    }

    public String loadHtmlDocument() throws IOException {
        String html = cachedHtml;
        if (html != null) {
            return html;
        }
        synchronized (this) {
            if (cachedHtml != null) {
                return cachedHtml;
            }
            Path assetRoot = weizhiRepo.resolve("android/agent-tools-webview/src/main/assets/weizhi-web");
            String bridge = Files.readString(assetRoot.resolve("bridge.js"), StandardCharsets.UTF_8);
            String template = Files.readString(assetRoot.resolve("bootstrap.html"), StandardCharsets.UTF_8);
            if (!template.contains(PLACEHOLDER)) {
                throw new IOException("bootstrap.html 缺少占位符 " + PLACEHOLDER);
            }
            String injected = template.replace(
                PLACEHOLDER,
                CDP_NATIVE_BRIDGE_SHIM + "\n" + bridge
            );
            cachedHtml = injected;
            return cachedHtml;
        }
    }
}
