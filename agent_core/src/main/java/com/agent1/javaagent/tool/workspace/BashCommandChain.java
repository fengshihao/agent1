package com.agent1.javaagent.tool.workspace;

import java.util.ArrayList;
import java.util.List;

/** 解析后的 bash 链：{@code a && b}、{@code a ; b}、管道与重定向。 */
final class BashCommandChain {

    enum Join {
        /** 总是执行下一段 */
        SEMICOLON,
        /** 上一段 exit 0 才执行 */
        AND
    }

    enum RedirectMode {
        NONE,
        OVERWRITE,
        APPEND
    }

    static final class Redirect {
        final RedirectMode mode;
        final String path;

        Redirect(RedirectMode mode, String path) {
            this.mode = mode;
            this.path = path == null ? "" : path;
        }

        static Redirect none() {
            return new Redirect(RedirectMode.NONE, "");
        }
    }

    static final class Stage {
        final String[] argv;
        final Redirect stdin;
        final Redirect stdout;

        Stage(String[] argv, Redirect stdin, Redirect stdout) {
            this.argv = argv;
            this.stdin = stdin == null ? Redirect.none() : stdin;
            this.stdout = stdout == null ? Redirect.none() : stdout;
        }
    }

    static final class Segment {
        final List<Stage> pipe;
        final Join joinToNext;

        Segment(List<Stage> pipe, Join joinToNext) {
            this.pipe = List.copyOf(pipe);
            this.joinToNext = joinToNext;
        }
    }

    private BashCommandChain() {
    }

    static List<Segment> parse(String command) {
        List<Part> parts = splitTopLevel(command);
        List<Segment> segments = new ArrayList<>();
        for (Part part : parts) {
            segments.add(parseSegment(part.text, part.joinToNext));
        }
        return segments;
    }

    private record Part(String text, Join joinToNext) {
    }

    private static List<Part> splitTopLevel(String command) {
        List<Part> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < command.length()) {
            if (matchesAt(command, i, "&&")) {
                flushPart(out, current, Join.AND);
                i += 2;
                continue;
            }
            if (command.charAt(i) == ';') {
                flushPart(out, current, Join.SEMICOLON);
                i++;
                continue;
            }
            if (command.charAt(i) == '"') {
                i = readDoubleQuoted(command, i + 1, current);
                continue;
            }
            if (command.charAt(i) == '\'') {
                i = readSingleQuoted(command, i + 1, current);
                continue;
            }
            current.append(command.charAt(i));
            i++;
        }
        flushPart(out, current, Join.SEMICOLON);
        if (!out.isEmpty()) {
            Part last = out.get(out.size() - 1);
            out.set(out.size() - 1, new Part(last.text, Join.SEMICOLON));
        }
        return out;
    }

    private static void flushPart(List<Part> out, StringBuilder current, Join join) {
        String text = current.toString().trim();
        current.setLength(0);
        if (!text.isEmpty()) {
            out.add(new Part(text, join));
        }
    }

    private static Segment parseSegment(String text, Join joinToNext) {
        List<String> pipeChunks = splitOutsideQuotes(text, '|');
        List<Stage> stages = new ArrayList<>();
        for (int p = 0; p < pipeChunks.size(); p++) {
            String chunk = pipeChunks.get(p).trim();
            Redirect stdin = Redirect.none();
            Redirect stdout = Redirect.none();
            if (p == 0) {
                InputSplit in = parseInputRedirect(chunk);
                chunk = in.commandText;
                stdin = in.stdin;
            }
            if (p == pipeChunks.size() - 1) {
                ParsedRedirect pr = parseOutputRedirect(chunk);
                chunk = pr.text;
                stdout = pr.redirect;
            }
            String[] argv = BashTool.tokenize(chunk.trim());
            if (argv.length == 0) {
                throw new IllegalArgumentException("empty command in chain");
            }
            stages.add(new Stage(argv, stdin, stdout));
        }
        return new Segment(stages, joinToNext);
    }

    private static final class InputSplit {
        final String commandText;
        final Redirect stdin;

        InputSplit(String commandText, Redirect stdin) {
            this.commandText = commandText;
            this.stdin = stdin;
        }
    }

    private static InputSplit parseInputRedirect(String chunk) {
        int lt = indexOutsideQuotes(chunk, '<');
        if (lt < 0) {
            return new InputSplit(chunk, Redirect.none());
        }
        String left = chunk.substring(0, lt).trim();
        String right = chunk.substring(lt + 1).trim();
        String[] tokens = BashTool.tokenize(right);
        if (tokens.length != 1) {
            throw new IllegalArgumentException("input redirect expects one path");
        }
        return new InputSplit(left, new Redirect(RedirectMode.OVERWRITE, tokens[0]));
    }

    private static final class ParsedRedirect {
        final String text;
        final Redirect redirect;

        ParsedRedirect(String text, Redirect redirect) {
            this.text = text;
            this.redirect = redirect;
        }
    }

    private static ParsedRedirect parseOutputRedirect(String chunk) {
        int append = indexOutsideQuotes(chunk, ">>");
        if (append >= 0) {
            String left = chunk.substring(0, append).trim();
            String right = chunk.substring(append + 2).trim();
            String[] tokens = BashTool.tokenize(right);
            if (tokens.length != 1) {
                throw new IllegalArgumentException("append redirect expects one path");
            }
            return new ParsedRedirect(left, new Redirect(RedirectMode.APPEND, tokens[0]));
        }
        int gt = indexOutsideQuotes(chunk, '>');
        if (gt >= 0) {
            String left = chunk.substring(0, gt).trim();
            String right = chunk.substring(gt + 1).trim();
            String[] tokens = BashTool.tokenize(right);
            if (tokens.length != 1) {
                throw new IllegalArgumentException("output redirect expects one path");
            }
            return new ParsedRedirect(left, new Redirect(RedirectMode.OVERWRITE, tokens[0]));
        }
        return new ParsedRedirect(chunk, Redirect.none());
    }

    private static List<String> splitOutsideQuotes(String text, char sep) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                sb.append(c);
                i = readDoubleQuoted(text, i + 1, sb);
                sb.append('"');
                continue;
            }
            if (c == '\'') {
                sb.append(c);
                i = readSingleQuoted(text, i + 1, sb);
                sb.append('\'');
                continue;
            }
            if (c == sep) {
                parts.add(sb.toString());
                sb.setLength(0);
                i++;
                continue;
            }
            sb.append(c);
            i++;
        }
        parts.add(sb.toString());
        return parts;
    }

    private static int indexOutsideQuotes(String text, char needle) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i = readDoubleQuoted(text, i + 1, new StringBuilder());
                continue;
            }
            if (c == '\'') {
                i = readSingleQuoted(text, i + 1, new StringBuilder());
                continue;
            }
            if (c == needle) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOutsideQuotes(String text, String needle) {
        for (int i = 0; i <= text.length() - needle.length(); i++) {
            if (matchesAt(text, i, needle) && !insideQuotesAt(text, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean insideQuotesAt(String text, int index) {
        boolean d = false;
        boolean s = false;
        for (int i = 0; i < index; i++) {
            char c = text.charAt(i);
            if (c == '"' && !s) {
                d = !d;
            } else if (c == '\'' && !d) {
                s = !s;
            }
        }
        return d || s;
    }

    private static boolean matchesAt(String text, int index, String needle) {
        return text.regionMatches(index, needle, 0, needle.length());
    }

    private static int readDoubleQuoted(String text, int i, StringBuilder sb) {
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                return i + 1;
            }
            if (c == '\\' && i + 1 < text.length()) {
                sb.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return i;
    }

    private static int readSingleQuoted(String text, int i, StringBuilder sb) {
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\'') {
                return i + 1;
            }
            sb.append(c);
            i++;
        }
        return i;
    }
}
