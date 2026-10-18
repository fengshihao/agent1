package com.agent1.javaagent.weizhi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** JS 堆/IO 上限默认 128MB（C 默认 32MB 曾卡住 html2pptx 8 页 deck，20-A T5b）。 */
class WeizhiRuntimeOptionsTest {

    private static final long MB = 1024 * 1024;

    @Test
    void defaultLimitsAre128MbAndEnvClamps() {
        assertEquals(128 * MB, WeizhiRuntimeOptions.defaultLimits(null).jsHeapBytes);
        assertEquals(64 * MB, WeizhiRuntimeOptions.defaultLimits("64").jsHeapBytes);
        assertEquals(16 * MB, WeizhiRuntimeOptions.defaultLimits("1").jsHeapBytes, "下限 16MB");
        assertEquals(1024 * MB, WeizhiRuntimeOptions.defaultLimits("99999").jsHeapBytes, "上限 1024MB");
        assertEquals(128 * MB, WeizhiRuntimeOptions.defaultLimits("abc").jsHeapBytes, "非法值回默认");
        assertEquals(128 * MB, new WeizhiRuntimeOptions().limits().jsHeapBytes);
        assertEquals(128 * MB, new WeizhiRuntimeOptions().limits().fsIoBytes, "单次 fs IO 同值");
    }
}
