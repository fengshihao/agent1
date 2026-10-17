package com.agent1.javaagent.weizhi.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 从 weizhi 仓库或 agent1 仓库读取 bootstrap.html + bridge.js，并注入 CDP 用 {@code NativeBridge} 垫片。 */
public final class DesktopBootstrapAssets {

    static final String PLACEHOLDER = "/*__BRIDGE_JS__*/";
    /** weizhi 仓库旧布局（agent1 fd58780 把资产迁走前的路径）。 */
    static final String LEGACY_ASSET_DIR = "android/agent-tools-webview/src/main/assets/weizhi-web";
    /** 资产迁移（agent1 fd58780）后位于 agent1 仓库 android assets。 */
    static final String AGENT1_ASSET_DIR = "android_agent/app/src/weizhi/assets/weizhi-web";
    private static final String CDP_NATIVE_BRIDGE_SHIM = """
        window.__agentBindingQueue = window.__agentBindingQueue || [];
        function __agentEmit(msg) {
          // Runtime.addBinding 生效后直接回调；仅绑定尚未就绪时入队，
          // 由 Java 侧 pollBindingQueue 兜底——两条通道同时投递会让分块结果 seq 重复。
          if (typeof agentNativeBridge === 'function') {
            agentNativeBridge(msg);
          } else {
            window.__agentBindingQueue.push(msg);
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
            Path assetRoot = resolveAssetRoot();
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

    /**
     * 解析 weizhi-web 资产目录。weizhi 仓库在 agent1 fd58780 之后不再携带该模块，
     * 资产真源迁到 agent1 仓库 android assets，因此按序回退：
     * weizhi 旧布局 → weizhi 同级 agent1 → 从 weizhi 父目录/工作目录向上找 agent1 布局。
     */
    Path resolveAssetRoot() throws IOException {
        return resolveAssetRoot(weizhiRepo, Path.of(System.getProperty("user.dir", ".")));
    }

    static Path resolveAssetRoot(Path weizhiRepo, Path userDir) throws IOException {
        weizhiRepo = weizhiRepo.toAbsolutePath().normalize();
        Path legacy = weizhiRepo.resolve(LEGACY_ASSET_DIR);
        if (isAssetRoot(legacy)) {
            return legacy;
        }
        Path viaSibling = weizhiRepo.resolveSibling("agent1").normalize().resolve(AGENT1_ASSET_DIR);
        if (isAssetRoot(viaSibling)) {
            return viaSibling;
        }
        for (Path base : new Path[] { weizhiRepo.getParent(), userDir }) {
            Path cursor = base;
            for (int up = 0; up < 4 && cursor != null; up++) {
                Path candidate = cursor.resolve(AGENT1_ASSET_DIR).normalize();
                if (isAssetRoot(candidate)) {
                    return candidate;
                }
                cursor = cursor.getParent();
            }
        }
        throw new IOException("未找到 weizhi-web 资产（bootstrap.html/bridge.js）："
            + "已尝试 " + weizhiRepo + " 下 " + LEGACY_ASSET_DIR
            + " 与 agent1 仓库 " + AGENT1_ASSET_DIR
            + "；请同步 weizhi 或在 agent1 仓库内运行");
    }

    private static boolean isAssetRoot(Path dir) {
        return Files.isRegularFile(dir.resolve("bridge.js"))
            && Files.isRegularFile(dir.resolve("bootstrap.html"));
    }
}
