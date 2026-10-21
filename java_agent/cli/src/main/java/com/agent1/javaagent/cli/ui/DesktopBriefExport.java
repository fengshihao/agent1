package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.brief.BriefChatExport;
import com.agent1.javaagent.model.AgentMessage;
import java.util.List;

/** @deprecated 使用 {@link BriefChatExport}；保留类型供旧引用。 */
@Deprecated
public final class DesktopBriefExport {

    private DesktopBriefExport() {
    }

    public static String format(List<AgentMessage> messages, String sessionId, String model) {
        BriefChatExport.Context context = new BriefChatExport.Context(
            sessionId,
            model == null ? "" : model,
            "desktop-web-ui",
            "",
            "",
            ""
        );
        return BriefChatExport.format(messages, "", context);
    }
}
