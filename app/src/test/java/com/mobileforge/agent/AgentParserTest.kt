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

    @Test
    fun parsesXmlToolCall() {
        val action = AgentParser.parse("текст <tool_call>project.list</tool_call>")
        assertEquals("project.list", action.tool)
        assertTrue(!action.done)
    }

    @Test
    fun parsesXmlToolWithArgs() {
        val raw = "<tool_call>project.create<arg_key>name</arg_key><arg_value>ЗвёздныйСборщик</arg_value><arg_key>type</arg_key><arg_value>3d</arg_value></tool_call>"
        val action = AgentParser.parse(raw)
        assertEquals("project.create", action.tool)
        assertEquals("ЗвёздныйСборщик", action.args.getString("name"))
        assertTrue(!action.done)
    }

    @Test
    fun firstJsonWhenTwoObjects() {
        val raw = "Let {\"tool\":\"project.list\",\"say\":\"смотрю\"} ... {\"tool\":\"project.list\",\"say\":\"ещё\"}"
        val action = AgentParser.parse(raw)
        assertEquals("project.list", action.tool)
        assertTrue(!action.done)
    }

    @Test
    fun displayStripsXml() {
        val view = AgentParser.display("привет <tool_call>project.list</tool_call>")
        assertTrue(!view.say.contains("tool_call"))
        assertTrue(!view.thinking.contains("<tool_call>"))
    }
}
