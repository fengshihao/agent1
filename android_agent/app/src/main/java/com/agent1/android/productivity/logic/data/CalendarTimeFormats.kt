package com.agent1.android.productivity.logic.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 解析脚本传入的 ISO8601 / 常见日期字符串，供日历读写共用。 */
object CalendarTimeFormats {

    private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val ISO_LOCAL: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseToEpochMillis(text: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return try {
            Instant.parse(trimmed).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(trimmed, ISO_OFFSET).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(trimmed, ISO_LOCAL).atZone(zone).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(trimmed, ISO_DATE).atStartOfDay(zone).toInstant().toEpochMilli()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }

    fun formatIso(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(ISO_OFFSET)
    }
}
