package com.agent1.android.productivity.logic.business.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.agent1.android.productivity.logic.data.AndroidCalendarAccess
import com.agent1.android.productivity.logic.data.AndroidContactsAccess
import com.agent1.android.productivity.logic.data.AndroidLocationAccess
import com.agent1.android.productivity.logic.data.HostCapabilityPreferences
import com.weizhi.WeizhiEngine
import com.weizhi.platform.MiniJson
import org.json.JSONObject

/**
 * Agent1 扩展原生 Caps：在 [com.weizhi.caps.AndroidCaps] 之后链式挂载 __caps，
 * 并注入 calendar / location / contacts JS API（OpenMinis offload 的 Agent1 等价物）。
 */
object Agent1NativeCapsExtension {

    private const val NATIVE_CAPS_JS = """
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
      return capCall('calendar.create', {
        title: String(spec.title || ''),
        start: String(spec.start || ''),
        end: spec.end != null ? String(spec.end) : '',
        notes: spec.notes != null ? String(spec.notes) : '',
        location: spec.location != null ? String(spec.location) : '',
        allDay: !!spec.allDay
      });
    }
  };
  android.location = {
    getCurrent: function(spec){
      spec = spec || {};
      var extra = {};
      if (spec.coarse) extra.coarse = true;
      if (spec.timeoutMs != null) extra.timeoutMs = spec.timeoutMs;
      return capCall('location.getCurrent', extra);
    }
  };
  android.contacts = {
    search: function(spec){
      spec = spec || {};
      return capCall('contacts.search', {
        query: String(spec.query || ''),
        limit: spec.limit != null ? spec.limit : 20,
        phones: !!spec.phones,
        unmask: !!spec.unmask
      });
    }
  };
})();
"""

    fun install(engine: WeizhiEngine, appContext: Context) {
        val inner = engine.getHostCall()
        val prefs = HostCapabilityPreferences(appContext)
        val calendar = AndroidCalendarAccess(appContext)
        val location = AndroidLocationAccess(appContext)
        val contacts = AndroidContactsAccess(appContext)
        engine.setHostCall { argsJson ->
            dispatch(argsJson, appContext, prefs, calendar, location, contacts, inner)
        }
        engine.runJs(NATIVE_CAPS_JS, 5000)
    }

    private fun dispatch(
        argsJson: String,
        context: Context,
        prefs: HostCapabilityPreferences,
        calendar: AndroidCalendarAccess,
        location: AndroidLocationAccess,
        contacts: AndroidContactsAccess,
        inner: WeizhiEngine.HostCall?,
    ): String {
        return try {
            val args = MiniJson.`object`(argsJson)
            val op = MiniJson.str(args, "op")
            when (op) {
                "calendar.list" -> handleList(context, prefs, calendar, args)
                "calendar.create" -> handleCreate(context, prefs, calendar, args)
                "location.getCurrent" -> handleLocation(context, prefs, location, args)
                "contacts.search" -> handleContactsSearch(context, prefs, contacts, args)
                else -> inner?.call(argsJson) ?: MiniJson.error("unsupported: $op")
            }
        } catch (e: Exception) {
            MiniJson.error(e.message ?: "native cap failed")
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
        val perm = ensurePermissions(context, Manifest.permission.READ_CALENDAR)
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
        return calendar.listEvents(today, days, start, end, limit).toString()
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
        ensurePermissions(context, Manifest.permission.READ_CALENDAR)?.let { return it }
        ensurePermissions(context, Manifest.permission.WRITE_CALENDAR)?.let { return it }
        val title = MiniJson.str(args, "title")
        val start = MiniJson.str(args, "start")
        val end = MiniJson.str(args, "end")
        val notes = MiniJson.str(args, "notes")
        val loc = MiniJson.str(args, "location")
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
        return calendar.createEvent(title, start, end, notes, loc, allDay).toString()
    }

    private fun handleLocation(
        context: Context,
        prefs: HostCapabilityPreferences,
        location: AndroidLocationAccess,
        args: Map<String, Any>,
    ): String {
        if (!prefs.isLocationEnabled()) {
            return jsonError("capability_disabled", "Location access is off in Agent1 settings")
        }
        val coarseOnly = args["coarse"] == true || "true" == args["coarse"]?.toString()
        val perms = if (coarseOnly) {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
        ensurePermissions(context, *perms)?.let { return it }
        if (!HostUiBridge.confirm("助手将读取当前位置（${if (coarseOnly) "粗略" else "精确"}定位）。")) {
            return jsonError("denied", "User declined location access")
        }
        val timeoutMs = MiniJson.longVal(args, "timeoutMs").let { if (it <= 0) 15_000L else it }
        return location.getCurrent(coarseOnly, timeoutMs).toString()
    }

    private fun handleContactsSearch(
        context: Context,
        prefs: HostCapabilityPreferences,
        contacts: AndroidContactsAccess,
        args: Map<String, Any>,
    ): String {
        if (!prefs.isContactsEnabled()) {
            return jsonError("capability_disabled", "Contacts access is off in Agent1 settings")
        }
        ensurePermissions(context, Manifest.permission.READ_CONTACTS)?.let { return it }
        val query = MiniJson.str(args, "query")
        val limit = MiniJson.intVal(args, "limit").let { if (it <= 0) 20 else it }
        val withPhones = args["phones"] == true || "true" == args["phones"]?.toString()
        val unmask = args["unmask"] == true || "true" == args["unmask"]?.toString()
        if (!HostUiBridge.confirm("助手将搜索通讯录中与「$query」匹配的联系人。")) {
            return jsonError("denied", "User declined contacts search")
        }
        return contacts.search(query, limit, withPhones, unmask).toString()
    }

    private fun ensurePermissions(context: Context, vararg permissions: String): String? {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            return null
        }
        val result = HostUiBridge.requestPermissions(missing.toTypedArray())
        val stillMissing = permissions.filter { result[it] != true }
        return if (stillMissing.isEmpty()) {
            null
        } else {
            jsonError("permission_denied", "Missing permission: ${stillMissing.joinToString()}")
        }
    }

    private fun jsonError(code: String, message: String): String {
        return JSONObject().put("error", code).put("message", message).toString()
    }
}
