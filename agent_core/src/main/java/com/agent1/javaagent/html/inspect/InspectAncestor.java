package com.agent1.javaagent.html.inspect;

import java.util.List;

/** 审查模式祖先链节点（近 → 远）。 */
public record InspectAncestor(String tag, String id, List<String> classes, int nth) {

    public InspectAncestor {
        tag = tag == null ? "" : tag;
        id = id == null ? "" : id;
        classes = classes == null ? List.of() : List.copyOf(classes);
        if (nth < 1) {
            nth = 1;
        }
    }

    public InspectAncestor(String tag) {
        this(tag, "", List.of(), 1);
    }

    public InspectAncestor(String tag, String id, List<String> classes) {
        this(tag, id, classes, 1);
    }
}
