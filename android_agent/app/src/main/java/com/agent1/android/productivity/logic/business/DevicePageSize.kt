package com.agent1.android.productivity.logic.business

import android.system.Os
import android.system.OsConstants

/** 16KB 页设备上未对齐的 libweizhijni 会在加载时直接 SIGSEGV，Java catch / Toast 都看不到。 */
internal object DevicePageSize {
    fun prefersNoWeizhiNative(): Boolean {
        return runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) >= 16_384L }.getOrDefault(false)
    }
}
