package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.html.inspect.HtmlInspectScript;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 桌面 Web UI：HTML 预览壳（内嵌同源 iframe + 审查脚本注入）。 */
public final class HtmlPreviewShell {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HtmlPreviewShell() {
    }

    public static String render(String sessionId, String workspaceRelativePath) {
        String childSrc = WorkspaceFileUrls.workspaceFileUrl(sessionId, workspaceRelativePath);
        String installJsJson = jsonString(HtmlInspectScript.INSTALL_JS);
        String enableJsJson = jsonString(HtmlInspectScript.ENABLE_JS);
        String disableJsJson = jsonString(HtmlInspectScript.DISABLE_JS);
        String clearJsJson = jsonString(HtmlInspectScript.CLEAR_JS);
        return "<!DOCTYPE html>\n"
            + "<html lang=\"zh-CN\">\n"
            + "<head>\n"
            + "  <meta charset=\"utf-8\"/>\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n"
            + "  <title>preview</title>\n"
            + "  <style>\n"
            + "    html, body { margin: 0; height: 100%; background: #fff; }\n"
            + "    #preview-target { border: 0; width: 100%; height: 100%; display: block; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <iframe id=\"preview-target\" src=\"" + childSrc + "\"></iframe>\n"
            + "  <script>\n"
            + "    (function() {\n"
            + "      var INSTALL_JS = " + installJsJson + ";\n"
            + "      var ENABLE_JS = " + enableJsJson + ";\n"
            + "      var DISABLE_JS = " + disableJsJson + ";\n"
            + "      var CLEAR_JS = " + clearJsJson + ";\n"
            + "      var INSPECT_TYPE = " + jsonString(HtmlInspectScript.POST_MESSAGE_TYPE) + ";\n"
            + "      var frame = document.getElementById('preview-target');\n"
            + "      function runInChild(code) {\n"
            + "        if (!frame || !frame.contentWindow) return;\n"
            + "        try { frame.contentWindow.eval(code); } catch (e) { }\n"
            + "      }\n"
            + "      function installInChild() { runInChild(INSTALL_JS); }\n"
            + "      window.addEventListener('message', function(ev) {\n"
            + "        if (!ev.data || ev.data.type !== INSPECT_TYPE) return;\n"
            + "        try { window.parent.postMessage(ev.data, window.location.origin); } catch (e) { }\n"
            + "      });\n"
            + "      window.addEventListener('message', function(ev) {\n"
            + "        if (!ev.data || ev.data.type !== 'agent1-inspect-cmd') return;\n"
            + "        var cmd = ev.data.cmd;\n"
            + "        if (cmd === 'install') installInChild();\n"
            + "        else if (cmd === 'enable') runInChild(ENABLE_JS);\n"
            + "        else if (cmd === 'disable') runInChild(DISABLE_JS);\n"
            + "        else if (cmd === 'clear') runInChild(CLEAR_JS);\n"
            + "      });\n"
            + "      frame.addEventListener('load', installInChild);\n"
            + "    })();\n"
            + "  </script>\n"
            + "</body>\n"
            + "</html>\n";
    }

    private static String jsonString(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "\"\"";
        }
    }
}
