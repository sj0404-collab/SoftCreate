package com.mobileforge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentParserTest {
    @Test
    fun readsToolFromFence() {
        val action = AgentParser.parse("```json\n{\"tool\":\"project.create\",\"args\":{\"name\":\"A\"}}\n```")
        assertEquals("project.create", action.tool)
        assertEquals("A", action.args.getString("name"))
        assertTrue(!action.done)
    }

    @Test
    fun doneWithoutTool() {
        val action = AgentParser.parse("{\"done\":true,\"say\":\"ok\"}")
        assertTrue(action.done)
        assertEquals("ok", action.say)
    }

    @Test
    fun stripsNullSpamBeforeJson() {
        val action = AgentParser.parse(
            "inspect.nullnullnullnull{\"tool\":\"fs.list\",\"args\":{}}",
        )
        assertEquals("fs.list", action.tool)
    }

    @Test
    fun displayKeepsThinkingAndSayWithoutDumpingJson() {
        val view = AgentParser.display(
            "сначала мысль\n{\"tool\":\"asset.create\",\"say\":\"пишу лес\",\"args\":{}}",
            "думаю про биомы",
        )
        assertTrue(view.thinking.contains("биомы") || view.thinking.contains("мысль"))
        assertEquals("пишу лес", view.say)
        assertTrue(view.json.contains("asset.create"))
    }
}
