package com.agent1.javaagent.weizhi;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 轻量扫描脚本中的 {@code weizhi:<name>} import（不在 import 内联网）。 */
public final class WeizhiPackageImports {

    private static final Pattern FROM_WEIZHI =
        Pattern.compile("from\\s+['\"]weizhi:([a-z0-9][a-z0-9._-]*)['\"]", Pattern.MULTILINE);

    private WeizhiPackageImports() {
    }

    public static Set<String> scan(String source) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (source == null || source.isEmpty()) {
            return names;
        }
        Matcher matcher = FROM_WEIZHI.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
