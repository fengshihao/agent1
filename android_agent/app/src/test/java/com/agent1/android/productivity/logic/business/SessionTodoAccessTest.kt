package com.agent1.android.productivity.logic.business

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTodoAccessTest {

    private val mapper = ObjectMapper()

    @Test
    fun parseTodoListFromToolDetails_readsTodoListKind() {
        val node = mapper.readTree(
            """
            {"kind":"todo_list","todos":[
              {"id":"1","content":"第一步","status":"in_progress"},
              {"id":"2","content":"第二步","status":"pending"}
            ]}
            """.trimIndent(),
        )
        val items = SessionTodoAccess.parseTodoListFromToolDetails(node)
        assertEquals(2, items?.size)
        assertEquals("in_progress", items?.get(0)?.status)
    }

    @Test
    fun parseTodoListFromToolDetails_ignoresOtherKinds() {
        val node = mapper.readTree("""{"kind":"other"}""")
        assertNull(SessionTodoAccess.parseTodoListFromToolDetails(node))
    }
}
