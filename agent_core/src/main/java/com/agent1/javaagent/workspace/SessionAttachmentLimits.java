package com.agent1.javaagent.workspace;

/** 与 Android {@code SessionAttachmentLimits} 对齐。 */
public final class SessionAttachmentLimits {

    public static final int MAX_FILES_PER_SESSION = 10;
    public static final int MAX_FILE_SIZE_MB = 50;
    public static final long MAX_FILE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L;

    private SessionAttachmentLimits() {
    }
}
