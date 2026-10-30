package com.agent1.android.productivity.logic.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 通过 [CalendarContract] 读写系统日历（IO 层，不含权限 UI）。 */
class AndroidCalendarAccess(private val context: Context) {

    fun listEvents(
        today: Boolean,
        days: Int?,
        startIso: String?,
        endIso: String?,
        limit: Int,
    ): JSONObject {
        val zone = java.time.ZoneId.systemDefault()
        val (startMs, endMs) = resolveRange(today, days, startIso, endIso, zone)
            ?: return JSONObject().put("error", "bad_range").put("message", "invalid --start/--end or date range")
        val max = limit.coerceIn(1, 200)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID,
        )
        val uri = CalendarContract.Events.CONTENT_URI
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val args = arrayOf(startMs.toString(), endMs.toString())
        val sort = "${CalendarContract.Events.DTSTART} ASC LIMIT $max"
        val events = JSONArray()
        context.contentResolver.query(uri, projection, selection, args, sort)?.use { cursor ->
            while (cursor.moveToNext()) {
                events.put(cursorToEvent(cursor, zone))
            }
        }
        return JSONObject()
            .put("events", events)
            .put("start", CalendarTimeFormats.formatIso(startMs, zone))
            .put("end", CalendarTimeFormats.formatIso(endMs, zone))
            .put("count", events.length())
    }

    fun createEvent(
        title: String,
        startIso: String,
        endIso: String?,
        notes: String?,
        location: String?,
        allDay: Boolean,
    ): JSONObject {
        if (title.isBlank()) {
            return JSONObject().put("error", "bad_argument").put("message", "title is required")
        }
        val zone = java.time.ZoneId.systemDefault()
        val startMs = CalendarTimeFormats.parseToEpochMillis(startIso, zone)
            ?: return JSONObject().put("error", "bad_argument").put("message", "invalid start")
        val endMs = when {
            endIso.isNullOrBlank() -> startMs + TimeUnit.HOURS.toMillis(1)
            else -> CalendarTimeFormats.parseToEpochMillis(endIso, zone)
                ?: return JSONObject().put("error", "bad_argument").put("message", "invalid end")
        }
        val calId = pickWritableCalendarId()
            ?: return JSONObject().put("error", "no_calendar").put("message", "no writable calendar on device")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, if (endMs > startMs) endMs else startMs + TimeUnit.HOURS.toMillis(1))
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            if (!notes.isNullOrBlank()) {
                put(CalendarContract.Events.DESCRIPTION, notes.trim())
            }
            if (!location.isNullOrBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            }
        }
        val insertUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return JSONObject().put("error", "insert_failed").put("message", "Calendar provider rejected insert")
        val id = ContentUris.parseId(insertUri)
        return JSONObject()
            .put("id", id)
            .put("title", title.trim())
            .put("start", CalendarTimeFormats.formatIso(startMs, zone))
            .put("end", CalendarTimeFormats.formatIso(endMs.coerceAtLeast(startMs), zone))
            .put("calendarId", calId)
    }

    private fun cursorToEvent(cursor: Cursor, zone: java.time.ZoneId): JSONObject {
        val id = cursor.getLong(0)
        val title = cursor.getString(1).orEmpty()
        val start = cursor.getLong(2)
        val end = cursor.getLong(3)
        val loc = cursor.getString(4).orEmpty()
        val notes = cursor.getString(5).orEmpty()
        val allDay = cursor.getInt(6) != 0
        val calendarId = cursor.getLong(7)
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("start", CalendarTimeFormats.formatIso(start, zone))
            .put("end", CalendarTimeFormats.formatIso(end, zone))
            .put("location", loc)
            .put("notes", notes)
            .put("allDay", allDay)
            .put("calendarId", calendarId)
    }

    private fun resolveRange(
        today: Boolean,
        days: Int?,
        startIso: String?,
        endIso: String?,
        zone: java.time.ZoneId,
    ): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        if (today) {
            val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            return startOfDay to (startOfDay + TimeUnit.DAYS.toMillis(1))
        }
        if (days != null && days > 0) {
            val n = days.coerceAtMost(366)
            val start = now - TimeUnit.DAYS.toMillis(n.toLong())
            return start to now
        }
        val startMs = if (startIso.isNullOrBlank()) {
            now - TimeUnit.DAYS.toMillis(1)
        } else {
            CalendarTimeFormats.parseToEpochMillis(startIso, zone) ?: return null
        }
        val endMs = if (endIso.isNullOrBlank()) {
            now
        } else {
            CalendarTimeFormats.parseToEpochMillis(endIso, zone) ?: return null
        }
        return if (startMs > endMs) endMs to startMs else startMs to endMs
    }

    private fun pickWritableCalendarId(): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        var primary: Long? = null
        var first: Long? = null
        context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val level = cursor.getInt(1)
                val isPrimary = cursor.getInt(2) != 0
                if (level >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    if (first == null) {
                        first = id
                    }
                    if (isPrimary) {
                        primary = id
                    }
                }
            }
        }
        return primary ?: first
    }
}
