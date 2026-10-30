package com.agent1.android.productivity.logic.business.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.agent1.android.productivity.logic.data.AndroidCalendarAccess
import com.agent1.android.productivity.logic.data.HostCapabilityPreferences
import com.weizhi.WeizhiEngine
import com.weizhi.platform.MiniJson
import org.json.JSONObject

/**
 * Agent1 扩展原生 Caps：在 [com.weizhi.caps.AndroidCaps] 之后链式挂载 __caps，
 * 并注入 {@code android.calendar} JS API（Minis android-calendar 的 JS 等价物）。
 */
object Agent1NativeCapsExtension {

    private const val CALENDAR_JS = """
(function(){
  if (typeof android === 'undefined') return;
  function capCall(op, extra) {
    var req = {op: op};
    if (extra) { for (var k in extra) { if (Object.prototype.hasOwnProperty.call(extra,k)) req[k]=extra[k]; } }
    var r = __caps(req);
    if (r && r.error) throw new Error(r.error);
    return r;
  }
  android.calendar = {
    list: function(spec){
      spec = spec || {};
      var extra = {};
      if (spec.today) extra.today = true;
      if (spec.days != null) extra.days = spec.days;
      if (spec.start) extra.start = String(spec.start);
      if (spec.end) extra.end = String(spec.end);
      if (spec.limit != null) extra.limit = spec.limit;
      return capCall('calendar.list', extra);
    },
    create: function(spec){
      spec = spec || {};
      var extra = {
        title: String(spec.title || ''),
        start: String(spec.start || ''),
        end: spec.end != null ? String(spec.end) : '',
        notes: spec.notes != null ? String(spec.notes) : '',
        location: spec.location != null ? String(spec.location) : '',
        allDay: !!spec.allDay
      };
      return capCall('calendar.create', extra);
    }
  };
})();
"""

    fun install(engine: WeizhiEngine, appContext: Context) {
        val inner = engine.getHostCall()
        val prefs = HostCapabilityPreferences(appContext)
        val calendar = AndroidCalendarAccess(appContext)
        engine.setHostCall { argsJson ->
            dispatch(argsJson, appContext, prefs, calendar, inner)
        }
        engine.runJs(CALENDAR_JS, 5000)
    }

    private fun dispatch(
        argsJson: String,
        context: Context,
        prefs: HostCapabilityPreferences,
        calendar: AndroidCalendarAccess,
        inner: WeizhiEngine.HostCall?,
    ): String {
        return try {
            val args = MiniJson.`object`(argsJson)
            val op = MiniJson.str(args, "op")
            when (op) {
                "calendar.list" -> handleList(context, prefs, calendar, args)
                "calendar.create" -> handleCreate(context, prefs, calendar, args)
                else -> inner?.call(argsJson) ?: MiniJson.error("unsupported: $op")
            }
        } catch (e: Exception) {
            MiniJson.error(e.message ?: "calendar failed")
        }
    }

    private fun handleList(
        context: Context,
        prefs: HostCapabilityPreferences,
        calendar: AndroidCalendarAccess,
        args: Map<String, Any>,
    ): String {
        if (!prefs.isCalendarEnabled()) {
            return jsonError("capability_disabled", "Calendar access is off in Agent1 settings")
        }
        val perm = ensurePermission(context, Manifest.permission.READ_CALENDAR)
        if (perm != null) {
            return perm
        }
        val today = args["today"] == true || "true" == args["today"]?.toString()
        val days = args["days"]?.let {
            when (it) {
                is Number -> it.toInt()
                else -> it.toString().toIntOrNull()
            }
        }
        val start = MiniJson.str(args, "start")
        val end = MiniJson.str(args, "end")
        val limit = MiniJson.intVal(args, "limit").let { if (it <= 0) 50 else it }
        val body = calendar.listEvents(today, days, start, end, limit)
        return body.toString()
    }

    private fun handleCreate(
        context: Context,
        prefs: HostCapabilityPreferences,
        calendar: AndroidCalendarAccess,
        args: Map<String, Any>,
    ): String {
        if (!prefs.isCalendarEnabled()) {
            return jsonError("capability_disabled", "Calendar access is off in Agent1 settings")
        }
        val readOk = ensurePermission(context, Manifest.permission.READ_CALENDAR)
        if (readOk != null) {
            return readOk
        }
        val writeOk = ensurePermission(context, Manifest.permission.WRITE_CALENDAR)
        if (writeOk != null) {
            return writeOk
        }
        val title = MiniJson.str(args, "title")
        val start = MiniJson.str(args, "start")
        val end = MiniJson.str(args, "end")
        val notes = MiniJson.str(args, "notes")
        val location = MiniJson.str(args, "location")
        val allDay = args["allDay"] == true || "true" == args["allDay"]?.toString()
        val confirmMsg = buildString {
            append("助手将在系统日历中创建事件：\n")
            append(title)
            append("\n开始：")
            append(start)
            if (end.isNotBlank()) {
                append("\n结束：")
                append(end)
            }
        }
        if (!HostUiBridge.confirm(confirmMsg)) {
            return jsonError("denied", "User declined calendar create")
        }
        val body = calendar.createEvent(title, start, end, notes, location, allDay)
        return body.toString()
    }

    private fun ensurePermission(context: Context, permission: String): String? {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val result = HostUiBridge.requestPermissions(arrayOf(permission))
        val granted = result[permission] == true
        return if (granted) {
            null
        } else {
            jsonError("permission_denied", "Missing permission: $permission")
        }
    }

    private fun jsonError(code: String, message: String): String {
        return JSONObject().put("error", code).put("message", message).toString()
    }
}
