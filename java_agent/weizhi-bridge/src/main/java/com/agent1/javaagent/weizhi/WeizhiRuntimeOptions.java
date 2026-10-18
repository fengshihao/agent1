package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.script.ScriptToolBridge;
import com.weizhi.WeizhiLimits;

/** 宿主侧 Weizhi 引擎开关（桌面生产力 CLI 默认值）。 */
public final class WeizhiRuntimeOptions {

    /** 默认 JS 堆/单次 IO 上限 128MB（C 默认 32MB——html2pptx 8 页 deck 的回执+解码峰值超限，20-A T5b）。 */
    public static final long DEFAULT_MB = 128;

    public WeizhiRuntimeOptions() {
        this.limits = defaultLimits(System.getenv("AGENT1_WEIZHI_JS_HEAP_MB"));
    }

    /** AGENT1_WEIZHI_JS_HEAP_MB 覆盖默认（16–1024，非法值回默认）；jsHeap 与单次 fsIo 同值。 */
    static WeizhiLimits defaultLimits(String envMb) {
        long mb = DEFAULT_MB;
        if (envMb != null && envMb.matches("\\d+")) {
            mb = Math.max(16, Math.min(1024, Long.parseLong(envMb)));
        }
        WeizhiLimits limits = new WeizhiLimits();
        limits.jsHeapBytes = mb * 1024 * 1024;
        limits.fsIoBytes = mb * 1024 * 1024;
        return limits;
    }

    /** 浅拷贝，便于 open 前覆盖 nativePluginDir 等字段。 */
    public WeizhiRuntimeOptions(WeizhiRuntimeOptions from) {
        if (from == null) {
            return;
        }
        this.limits = from.limits;
        this.packageRoot = from.packageRoot;
        this.enableFetch = from.enableFetch;
        this.fetchHostAllowlist = from.fetchHostAllowlist;
        this.installDesktopCaps = from.installDesktopCaps;
        this.enableNativeMock = from.enableNativeMock;
        this.nativePluginDir = from.nativePluginDir;
        this.scriptToolBridge = from.scriptToolBridge;
        this.mcpAgentRoot = from.mcpAgentRoot;
    }

    public WeizhiRuntimeOptions copy() {
        return new WeizhiRuntimeOptions(this);
    }

    private WeizhiLimits limits;
    private String packageRoot;
    private boolean enableFetch;
    private String[] fetchHostAllowlist;
    private boolean installDesktopCaps = true;
    private boolean enableNativeMock;
    private String nativePluginDir;
    private ScriptToolBridge scriptToolBridge;
    private java.nio.file.Path mcpAgentRoot;

    public WeizhiLimits limits() {
        return limits;
    }

    public WeizhiRuntimeOptions limits(WeizhiLimits limits) {
        this.limits = limits;
        return this;
    }

    public String packageRoot() {
        return packageRoot;
    }

    public WeizhiRuntimeOptions packageRoot(String packageRoot) {
        this.packageRoot = packageRoot;
        return this;
    }

    public boolean enableFetch() {
        return enableFetch;
    }

    public WeizhiRuntimeOptions enableFetch(boolean enableFetch) {
        this.enableFetch = enableFetch;
        return this;
    }

    public String[] fetchHostAllowlist() {
        return fetchHostAllowlist;
    }

    public WeizhiRuntimeOptions fetchHostAllowlist(String[] fetchHostAllowlist) {
        this.fetchHostAllowlist = fetchHostAllowlist;
        return this;
    }

    public boolean installDesktopCaps() {
        return installDesktopCaps;
    }

    public WeizhiRuntimeOptions installDesktopCaps(boolean installDesktopCaps) {
        this.installDesktopCaps = installDesktopCaps;
        return this;
    }

    public boolean enableNativeMock() {
        return enableNativeMock;
    }

    public WeizhiRuntimeOptions enableNativeMock(boolean enableNativeMock) {
        this.enableNativeMock = enableNativeMock;
        return this;
    }

    public String nativePluginDir() {
        return nativePluginDir;
    }

    public WeizhiRuntimeOptions nativePluginDir(String nativePluginDir) {
        this.nativePluginDir = nativePluginDir;
        return this;
    }

    public ScriptToolBridge scriptToolBridge() {
        return scriptToolBridge;
    }

    public WeizhiRuntimeOptions scriptToolBridge(ScriptToolBridge scriptToolBridge) {
        this.scriptToolBridge = scriptToolBridge;
        return this;
    }

    public java.nio.file.Path mcpAgentRoot() {
        return mcpAgentRoot;
    }

    public WeizhiRuntimeOptions mcpAgentRoot(java.nio.file.Path mcpAgentRoot) {
        this.mcpAgentRoot = mcpAgentRoot == null ? null : mcpAgentRoot.toAbsolutePath().normalize();
        return this;
    }
}
