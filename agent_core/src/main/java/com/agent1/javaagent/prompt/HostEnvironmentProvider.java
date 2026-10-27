package com.agent1.javaagent.prompt;

import java.util.List;

/**
 * 系统提示词「当前环境状态」段的信息来源。
 * CLI 与 Android 宿主各自实现；未注入时 {@link ProductivitySystemPromptBuilder} 用桌面默认实现。
 * 返回空串 / 空列表表示省略该行，不渲染。
 * 只保留会改变模型决策的信息（平台、JS 运行环境、屏幕、目录权限）；内核版本、内存余量不影响决策，不报。
 */
public interface HostEnvironmentProvider {

    /** 平台一行，如「macOS 13.6」「Android 16」。 */
    String platformLine();

    /** 屏幕一行（逻辑像素，宽×高，如「1440×900」），供模型决定 HTML 版面、图片等产出物尺寸；空串省略。 */
    default String screenLine() {
        return "";
    }

    /** JS 运行环境一行，如「QuickJS 扩展，非 Node；不能 npm 或 require」。 */
    String jsRuntimeLine();

    /** 目录结构行，含各目录作用与只读/可写标注，每行一条（不带列表前缀）。 */
    List<String> directoryLines();
}
