package com.agent1.android.productivity.logic.business

object SessionAttachmentLimits {
    const val MAX_FILES_PER_SESSION = 10

    const val MAX_FILE_SIZE_MB = 50
    const val MAX_FILE_BYTES: Long = MAX_FILE_SIZE_MB.toLong() * 1024L * 1024L
}
