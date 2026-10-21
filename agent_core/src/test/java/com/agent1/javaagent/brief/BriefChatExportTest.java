package com.agent1.javaagent.brief;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class BriefChatExportTest {

    @Test
    void format_skipsVerboseToolJson_butKeepsUserAssistant() {
        List<AgentMessage> messages = List.of(
            AgentMessage.user("你好"),
            AgentMessage.toolResult("t1", "{\"ok\":true,\"outputPath\":\"a.png\",\"outputBytes\":100}", false),
            AgentMessage.assistant("已生成图片", List.of())
        );
        String text = BriefChatExport.format(messages, "测试会话", new BriefChatExport.Context());
        assertTrue(text.contains("你好"));
        assertTrue(text.contains("已生成图片"));
        assertFalse(text.contains("outputBytes"));
    }

    @Test
    void format_leadsWithLatestToolFailureForDebug() {
        List<AgentMessage> messages = List.of(
            AgentMessage.user("写个文件"),
            AgentMessage.assistant("", "先看目录", List.of(new ToolCall("t9", "write_file", "{\"path\":\"a.txt\"}"))),
            AgentMessage.toolResult("t9", "denied: read only", true)
        );
        BriefChatExport.Context context = new BriefChatExport.Context(
            "s1",
            "qwen",
            "",
            "",
            "",
            "no key"
        );
        String text = BriefChatExport.format(messages, "调试", context);
        assertTrue(text.contains("异常摘要"));
        assertTrue(text.contains("write_file"));
        assertTrue(text.contains("t9"));
        assertTrue(text.contains("denied: read only"));
        assertTrue(text.contains("思考：先看目录"));
        assertTrue(text.contains("configError: no key"));
        assertTrue(text.indexOf("异常摘要") < text.indexOf("**用户**"));
    }

    @Test
    void format_includesToolErrors() {
        List<AgentMessage> messages = List.of(
            AgentMessage.toolResult("t1", "{\"ok\":false,\"resultPreview\":\"timeout\"}", true)
        );
        String text = BriefChatExport.format(messages, "", new BriefChatExport.Context());
        assertTrue(text.contains("工具异常"));
    }

    @Test
    void compressAssistantContent_stripsCodeFence() {
        String longBody = "结论如下。\n```kotlin\n" + "x\n".repeat(80) + "```\n收尾。";
        String out = BriefChatExport.compressAssistantContent(longBody);
        assertFalse(out.contains("```"));
        assertTrue(out.contains("省略代码块"));
        assertTrue(out.contains("结论如下"));
        assertTrue(out.contains("收尾"));
    }

    @Test
    void format_includesAskUserQuestionsFromAssistantToolCall() {
        String askArgs = """
            {"title":"React PPT","questions":[
              {"id":"audience","prompt":"听众是谁？","type":"text"},
              {"id":"length","prompt":"页数","type":"single_choice","options":["5","10"]}
            ]}
            """.trim();
        List<AgentMessage> messages = List.of(
            AgentMessage.user("给我生成一个PPT，介绍React模型。"),
            AgentMessage.assistant("", List.of(new ToolCall("tc1", "ask_user", askArgs))),
            AgentMessage.toolResult("tc1", "已向用户提出 2 个问题，Run 已暂停等待回复。", false)
        );
        String text = BriefChatExport.format(messages, "React PPT", new BriefChatExport.Context());
        assertTrue(text.contains("听众是谁"));
        assertTrue(text.contains("ask_user"));
        assertTrue(text.contains("给我生成一个PPT"));
    }

    @Test
    void compressAssistantContent_truncatesVeryLongPlainText() {
        String body = "开头说明。" + "中".repeat(3_000) + "结尾总结。";
        String out = BriefChatExport.compressAssistantContent(body);
        assertTrue(out.contains("略去约"));
        assertTrue(out.contains("开头说明"));
        assertTrue(out.contains("结尾总结"));
        assertTrue(out.length() < body.length());
    }
}
