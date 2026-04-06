package com.dynamicui.demo.agent.service

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

data class CalendarEventRow(
    val id: Long,
    val title: String,
    val dtStart: Long,
    val dtEnd: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val location: String,
    val description: String
)

object CalendarAccess {

    fun hasReadCalendar(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWriteCalendar(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 列出 [startMs, endMs] 内**开始时间**落在区间内的日程（系统日历库）。
     */
    fun listEvents(
        context: Context,
        startMs: Long,
        endMs: Long,
        limit: Int
    ): Result<List<CalendarEventRow>> {
        if (!hasReadCalendar(context)) {
            return Result.failure(IllegalStateException("缺少 READ_CALENDAR 权限"))
        }
        if (startMs > endMs) {
            return Result.failure(IllegalArgumentException("start_ms 不能大于 end_ms"))
        }
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMs.toString(), endMs.toString())
        val sort = "${CalendarContract.Events.DTSTART} ASC"
        val out = ArrayList<CalendarEventRow>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sort
        )?.use { c ->
            val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
            val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val calIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (c.moveToNext() && out.size < limit) {
                val id = if (idIdx >= 0) c.getLong(idIdx) else -1L
                if (id < 0) continue
                out.add(
                    CalendarEventRow(
                        id = id,
                        title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else "",
                        dtStart = if (startIdx >= 0) c.getLong(startIdx) else 0L,
                        dtEnd = if (endIdx >= 0) c.getLong(endIdx) else 0L,
                        allDay = allDayIdx >= 0 && c.getInt(allDayIdx) == 1,
                        calendarId = if (calIdx >= 0) c.getLong(calIdx) else 0L,
                        location = if (locIdx >= 0) c.getString(locIdx).orEmpty() else "",
                        description = if (descIdx >= 0) c.getString(descIdx).orEmpty() else ""
                    )
                )
            }
        }
        return Result.success(out)
    }

    fun pickWritableCalendarId(context: Context): Long? {
        if (!hasReadCalendar(context)) return null
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        )?.use { c ->
            val idIdx = c.getColumnIndex(CalendarContract.Calendars._ID)
            val levelIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (c.moveToNext()) {
                val level = if (levelIdx >= 0) c.getInt(levelIdx) else 0
                if (level >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    if (idIdx >= 0) return c.getLong(idIdx)
                }
            }
        }
        return null
    }

    fun createEvent(
        context: Context,
        calendarId: Long?,
        title: String,
        startMs: Long,
        endMs: Long,
        description: String,
        allDay: Boolean
    ): Result<Long> {
        if (!hasWriteCalendar(context)) {
            return Result.failure(IllegalStateException("缺少 WRITE_CALENDAR 权限"))
        }
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("title 不能为空"))
        }
        if (endMs <= startMs) {
            return Result.failure(IllegalArgumentException("end_ms 必须大于 start_ms"))
        }
        val calId = calendarId ?: pickWritableCalendarId(context)
            ?: return Result.failure(IllegalStateException("未找到可写日历，请确认系统已登录日历账户"))
        val tz = if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz.id)
            put(CalendarContract.Events.EVENT_END_TIMEZONE, tz.id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return Result.failure(IllegalStateException("插入日程失败"))
        return Result.success(ContentUris.parseId(uri))
    }

    fun deleteEvent(context: Context, eventId: Long): Result<Boolean> {
        if (!hasWriteCalendar(context)) {
            return Result.failure(IllegalStateException("缺少 WRITE_CALENDAR 权限"))
        }
        if (eventId <= 0) {
            return Result.failure(IllegalArgumentException("event_id 无效"))
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val n = context.contentResolver.delete(uri, null, null)
        return Result.success(n == 1)
    }
}
