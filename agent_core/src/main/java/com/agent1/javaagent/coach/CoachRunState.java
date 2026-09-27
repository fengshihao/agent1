package com.agent1.javaagent.coach;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单次 Run 内 Coach 计数（如 script.fail_repeat）。 */
public final class CoachRunState {

    private final Map<String, Integer> scriptFailureCounts = new LinkedHashMap<>();

    public void clear() {
        scriptFailureCounts.clear();
    }

    public int recordScriptFailure(String scriptKey) {
        if (scriptKey == null || scriptKey.isBlank()) {
            return 0;
        }
        int next = scriptFailureCounts.getOrDefault(scriptKey, 0) + 1;
        scriptFailureCounts.put(scriptKey, next);
        return next;
    }
}
