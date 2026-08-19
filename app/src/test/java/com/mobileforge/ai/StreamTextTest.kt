package com.mobileforge.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTextTest {
    @Test
    fun ignoresJsonNullContent() {
        val delta = JSONObject("""{"content":null,"reasoning_content":null}""")
        assertEquals("", StreamText.jsonText(delta, "content"))
        assertEquals("", StreamText.jsonText(delta, "reasoning_content", "thinking"))
    }

    @Test
    fun readsReasoningAndContent() {
        val choice = JSONObject(
            """{"delta":{"content":null,"reasoning_content":"think "}}""",
        )
        val first = StreamText.deltaOf(choice)
        assertEquals("", first.first)
        assertEquals("think ", first.second)
        val second = StreamText.deltaOf(JSONObject("""{"delta":{"content":"{\"tool\":\"fs.list\"}"}}"""))
        assertEquals("""{"tool":"fs.list"}""", second.first)
    }

    @Test
    fun stripsNullSpam() {
        val raw = "Use done.nullnullnullnull{\"done\":true,\"say\":\"ok\"}"
        assertEquals("Use done.{\"done\":true,\"say\":\"ok\"}", StreamText.stripNullSpam(raw))
        assertEquals("", StreamText.clean("null"))
        assertEquals("hello", StreamText.clean("hello"))
    }

    @Test
    fun remapDoesNotSendGeminiToZen() {
        val id = ModelCatalog.remap(Provider.ZEN_DIRECT, "gemini-2.0-flash")
        assertTrue(id.contains("laguna") || id.endsWith("-free") || id == "big-pickle")
        assertTrue(!id.startsWith("gemini"))
    }

    @Test
    fun planAutoHasZenFree() {
        val plan = ModelCatalog.plan(Provider.ZEN_DIRECT, "auto") { false }
        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.first == Provider.ZEN_DIRECT })
        assertTrue(plan.none { it.second == "auto" })
    }
}
