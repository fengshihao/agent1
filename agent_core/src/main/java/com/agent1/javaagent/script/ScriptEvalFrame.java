package com.agent1.javaagent.script;

/** execute_script 一次求值的用户源与 prelude 行数（D1）。 */
public final class ScriptEvalFrame {

    public enum SourceKind {
        INLINE,
        FILE
    }

    private final SourceKind sourceKind;
    private final String filePath;
    private final int agentArgsLines;
    private final int weizhiToolsLines;
    private final int userSourceLines;

    public ScriptEvalFrame(
        SourceKind sourceKind,
        String filePath,
        int agentArgsLines,
        int weizhiToolsLines,
        int userSourceLines
    ) {
        this.sourceKind = sourceKind;
        this.filePath = filePath == null ? "" : filePath;
        this.agentArgsLines = Math.max(agentArgsLines, 0);
        this.weizhiToolsLines = Math.max(weizhiToolsLines, 0);
        this.userSourceLines = Math.max(userSourceLines, 0);
    }

    public SourceKind sourceKind() {
        return sourceKind;
    }

    public String filePath() {
        return filePath;
    }

    public int agentArgsLines() {
        return agentArgsLines;
    }

    public int weizhiToolsLines() {
        return weizhiToolsLines;
    }

    public int userSourceLines() {
        return userSourceLines;
    }

    public int totalPreludeLines() {
        return agentArgsLines + weizhiToolsLines;
    }

    public String scriptKey(String inlineSource) {
        if (sourceKind == SourceKind.FILE && !filePath.isBlank()) {
            return "file:" + filePath;
        }
        String src = inlineSource == null ? "" : inlineSource;
        return "inline:" + Integer.toHexString(src.hashCode());
    }

    public static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    public static int countPreludeLines(String prelude) {
        if (prelude == null || prelude.isEmpty()) {
            return 0;
        }
        return countLines(prelude);
    }
}
