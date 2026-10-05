package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.script.ScriptToolBridge;
import com.weizhi.WeizhiLimits;

/** 宿主侧 Weizhi 引擎开关（桌面生产力 CLI 默认值）。 */
public final class WeizhiRuntimeOptions {

    public WeizhiRuntimeOptions() {
    }

    /** 浅拷贝，便于 open 前覆盖 nativePluginDir 等字段。 */
    public WeizhiRuntimeOptions(WeizhiRuntimeOptions from) {
        if (from == null) {
            return;
        }
        this.limits = from.limits;
        this.scriptFolder = from.scriptFolder;
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
    private String scriptFolder;
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

    public String scriptFolder() {
        return scriptFolder;
    }

    public WeizhiRuntimeOptions scriptFolder(String scriptFolder) {
        this.scriptFolder = scriptFolder;
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
