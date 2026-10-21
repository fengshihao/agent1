package com.agent1.javaagent.html.inspect;

import java.util.List;

/**
 * 从选中元素生成稳健 CSS 选择器（18 号规划 Phase B）。
 * 优先级：#id → 最近带 id 祖先 + tag → tag.firstClass → tag:nth-of-type(n) 祖先链。
 */
public final class ElementSelectorBuilder {

    private static final int MAX_PATH_ANCESTORS = 3;

    private ElementSelectorBuilder() {
    }

    public static String build(InspectedElement element) {
        String targetId = element.id().trim();
        if (!targetId.isEmpty()) {
            return "#" + cssEscape(targetId);
        }

        InspectAncestor ancestorWithId = null;
        for (InspectAncestor anc : element.ancestors()) {
            if (!anc.id().trim().isEmpty()) {
                ancestorWithId = anc;
                break;
            }
        }
        if (ancestorWithId != null) {
            return "#" + cssEscape(ancestorWithId.id().trim()) + " " + element.tag();
        }

        if (!element.classes().isEmpty()) {
            return element.tag() + "." + cssEscape(element.classes().get(0));
        }

        List<InspectAncestor> ancestors = element.ancestors();
        int limit = Math.min(MAX_PATH_ANCESTORS, ancestors.size());
        if (limit == 0) {
            return element.tag();
        }
        StringBuilder path = new StringBuilder();
        for (int i = limit - 1; i >= 0; i--) {
            InspectAncestor anc = ancestors.get(i);
            path.append(anc.tag());
            path.append(":nth-of-type(");
            path.append(anc.nth());
            path.append(") ");
        }
        path.append(element.tag());
        return path.toString();
    }

    private static String cssEscape(String raw) {
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                cleaned.append(c);
            }
        }
        return cleaned.isEmpty() ? raw : cleaned.toString();
    }
}
