package com.agent1.javaagent.coach;

/** {@code AGENT1_COACH}：默认开启；{@code 0} / {@code false} 关闭。 */
public final class CoachSettings {

    public static final int DEFAULT_LARGE_WRITE_BYTES = 65_536;
    public static final int DEFAULT_INLINE_LONG_LINES = 80;
    public static final int DEFAULT_INLINE_LONG_BYTES = 8_192;

    private CoachSettings() {
    }

    public static boolean enabledFromEnv() {
        String value = System.getenv("AGENT1_COACH");
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        return !"0".equals(trimmed) && !"false".equalsIgnoreCase(trimmed);
    }
}
