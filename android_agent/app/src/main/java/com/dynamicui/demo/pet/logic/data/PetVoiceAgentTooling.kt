package com.dynamicui.demo.pet.logic.data

import android.content.Context
import com.agent1.javaagent.tool.AgentTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.ActOnUiTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.CreateCalendarEventTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.DeleteCalendarEventTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.ExtractMainContentTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.GetCurrentPageSnapshotTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.GetShellCapabilitiesTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.ListCalendarEventsTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.QueryMediaStoreTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.RunIntentTool
import com.dynamicui.demo.pet.logic.data.accessibility.tools.RunShellTool

/** 语音宠物 Agent 的默认工具表装配（数据/设备访问实现均在此集中注册）。 */
object PetVoiceAgentTooling {
    fun buildTools(appContext: Context): List<AgentTool> = listOf(
        GetCurrentPageSnapshotTool(),
        GetShellCapabilitiesTool(),
        QueryMediaStoreTool(appContext),
        RunIntentTool(appContext),
        ActOnUiTool(),
        ListCalendarEventsTool(appContext),
        CreateCalendarEventTool(appContext),
        DeleteCalendarEventTool(appContext),
        ExtractMainContentTool(),
        RunShellTool()
    )
}
