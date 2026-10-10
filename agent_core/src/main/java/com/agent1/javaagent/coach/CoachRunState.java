package com.agent1.javaagent.coach;

/** 单次 Run 内 Coach 计数（如 script.fail_repeat、find_caps）。 */
public final class CoachRunState {

    private final java.util.Map<String, Integer> scriptFailureCounts = new java.util.LinkedHashMap<>();
    private int capabilitySearchCalls;
    private int capabilitySearchEmptyCalls;
    private boolean capabilitySearchUsed;
    private int bashHostToolProbeCoachCount;
    private int libHuntCoachCount;

    public void clear() {
        scriptFailureCounts.clear();
        capabilitySearchCalls = 0;
        capabilitySearchEmptyCalls = 0;
        capabilitySearchUsed = false;
        bashHostToolProbeCoachCount = 0;
        libHuntCoachCount = 0;
    }

    public int recordScriptFailure(String scriptKey) {
        if (scriptKey == null || scriptKey.isBlank()) {
            return 0;
        }
        int next = scriptFailureCounts.getOrDefault(scriptKey, 0) + 1;
        scriptFailureCounts.put(scriptKey, next);
        return next;
    }

    /** @return 本 Run 内 find_caps 累计调用次数 */
    public int recordCapabilitySearch(boolean emptyResult) {
        capabilitySearchUsed = true;
        capabilitySearchCalls++;
        if (emptyResult) {
            capabilitySearchEmptyCalls++;
        }
        return capabilitySearchCalls;
    }

    public int capabilitySearchEmptyCalls() {
        return capabilitySearchEmptyCalls;
    }

    public boolean capabilitySearchUsed() {
        return capabilitySearchUsed;
    }

    /** @return 本 Run 内因 bash 探测主机工具而追加 coach 的次数 */
    public int recordBashHostToolProbeCoach() {
        bashHostToolProbeCoachCount++;
        return bashHostToolProbeCoachCount;
    }

    /** @return 本 Run 内因「找不到内置脚本库」而追加 coach 的次数 */
    public int recordLibHuntCoach() {
        libHuntCoachCount++;
        return libHuntCoachCount;
    }
}
