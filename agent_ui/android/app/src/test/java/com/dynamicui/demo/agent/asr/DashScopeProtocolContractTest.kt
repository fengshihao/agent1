package com.dynamicui.demo.agent.asr

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DashScopeProtocolContractTest {

    @Test
    fun runTaskMessage_containsRequiredProtocolFields() {
        val msg = DashScopeFunAsrSession.buildRunTaskMessageText(
            taskId = "abc123taskid",
            sampleRate = 16000
        )
        val root = Json.parseToJsonElement(msg).jsonObject

        val header = root.getValue("header").jsonObject
        val payload = root.getValue("payload").jsonObject
        val parameters = payload.getValue("parameters").jsonObject

        assertEquals("run-task", header.getValue("action").jsonPrimitive.content)
        assertEquals("abc123taskid", header.getValue("task_id").jsonPrimitive.content)
        assertEquals("duplex", header.getValue("streaming").jsonPrimitive.content)

        assertEquals("audio", payload.getValue("task_group").jsonPrimitive.content)
        assertEquals("asr", payload.getValue("task").jsonPrimitive.content)
        assertEquals("recognition", payload.getValue("function").jsonPrimitive.content)
        assertEquals("fun-asr-realtime", payload.getValue("model").jsonPrimitive.content)

        assertEquals("pcm", parameters.getValue("format").jsonPrimitive.content)
        assertEquals(16000, parameters.getValue("sample_rate").jsonPrimitive.int)

        // input 必须存在且为对象
        payload.getValue("input").jsonObject
    }

    @Test
    fun finishTaskMessage_containsRequiredProtocolFields() {
        val msg = DashScopeFunAsrSession.buildFinishTaskMessageText("xyz789taskid")
        val root = Json.parseToJsonElement(msg).jsonObject
        val header = root.getValue("header").jsonObject
        val payload = root.getValue("payload").jsonObject

        assertEquals("finish-task", header.getValue("action").jsonPrimitive.content)
        assertEquals("xyz789taskid", header.getValue("task_id").jsonPrimitive.content)
        assertEquals("duplex", header.getValue("streaming").jsonPrimitive.content)
        payload.getValue("input").jsonObject
    }

    @Test
    fun runTaskMessage_canRoundTripAsJsonText() {
        val raw = DashScopeFunAsrSession.buildRunTaskMessageText("task-round-trip", 16000)
        val parsed = Json.parseToJsonElement(raw).jsonObject
        val payload = parsed.getValue("payload").jsonObject
        assertEquals("audio", payload.getValue("task_group").jsonPrimitive.content)
    }
}

