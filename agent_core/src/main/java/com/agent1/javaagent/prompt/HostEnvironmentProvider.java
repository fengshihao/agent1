package com.agent1.javaagent.prompt;

import java.util.List;

/**
 * 系统提示词「当前环境状态」段的信息来源。
 * CLI 与 Android 宿主各自实现；未注入时 {@link ProductivitySystemPromptBuilder} 用桌面默认实现。
 * 返回空串 / 空列表表示省略该行，不渲染。
 */
public interface HostEnvironmentProvider {

    /** 平台一行，如「macOS 13.6 内核 Darwin 22.6.0」。 */
    String platformLine();

    /** JS 运行环境一行，如「QuickJS 扩展，非 Node；不能 npm 或 require」。 */
    String jsRuntimeLine();

    /** 内存一行，如「JVM 内存上限 512MB」。取不到返回空串。 */
    String memoryLine();

    /** 目录结构行，含各目录作用与只读/可写标注，每行一条（不带列表前缀）。 */
    List<String> directoryLines();
}