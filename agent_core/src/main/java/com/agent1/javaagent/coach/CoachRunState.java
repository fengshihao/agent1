package com.agent1.javaagent.coach;

/** 单次 Run 内 Coach 计数（如 script.fail_repeat、capability_search）。 */
public final class CoachRunState {

    private final java.util.Map<String, Integer> scriptFailureCounts = new java.util.LinkedHashMap<>();
    private int capabilitySearchCalls;
    private int capabilitySearchEmptyCalls;

    public void clear() {
        scriptFailureCounts.clear();
        capabilitySearchCalls = 0;
        capabilitySearchEmptyCalls = 0;
    }

    public int recordScriptFailure(String scriptKey) {
        if (scriptKey == null || scriptKey.isBlank()) {
            return 0;
        }
        int next = scriptFailureCounts.getOrDefault(scriptKey, 0) + 1;
        scriptFailureCounts.put(scriptKey, next);
        return next;
    }

    /** @return 本 Run 内 capability_search 累计调用次数 */
    public int recordCapabilitySearch(boolean emptyResult) {
        capabilitySearchCalls++;
        if (emptyResult) {
            capabilitySearchEmptyCalls++;
        }
        return capabilitySearchCalls;
    }

    public int capabilitySearchEmptyCalls() {
        return capabilitySearchEmptyCalls;
    }
}
