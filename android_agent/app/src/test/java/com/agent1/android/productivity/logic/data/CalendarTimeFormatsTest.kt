package com.agent1.android.productivity.logic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId

class CalendarTimeFormatsTest {

    @Test
    fun parseIsoOffsetDateTime() {
        val zone = ZoneId.of("Asia/Shanghai")
        val ms = CalendarTimeFormats.parseToEpochMillis("2026-10-31T10:00:00+08:00", zone)
        assertNotNull(ms)
        assertEquals("2026-10-31T10:00:00+08:00", CalendarTimeFormats.formatIso(ms!!, zone))
    }

    @Test
    fun parseLocalDateAtStartOfDay() {
        val zone = ZoneId.of("UTC")
        val ms = CalendarTimeFormats.parseToEpochMillis("2026-10-31", zone)
        assertNotNull(ms)
    }
}
