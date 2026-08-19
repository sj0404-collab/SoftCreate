package com.mobileforge.agent

import org.json.JSONObject

data class AgentCard(
    val id: Long,
    val title: String,
    val tool: String,
    val args: String,
    val result: String,
    val ms: Long,
    val ok: Boolean,
    val expanded: Boolean = false,
)

data class AgentAction(
    val tool: String?,
    val args: JSONObject,
    val done: Boolean,
    val say: String,
)

object AgentParser {
    fun parse(raw: String): AgentAction {
        val json = extractJson(raw) ?: return AgentAction(null, JSONObject(), true, raw.trim())
        val done = json.optBoolean("done") || json.optString("tool") == "done"
        val tool = json.optString("tool").ifBlank { null }
        val args = json.optJSONObject("args") ?: JSONObject()
        json.keys().forEach { key ->
            if (key != "tool" && key != "args" && key != "done" && key != "say") {
                if (!args.has(key)) args.put(key, json.get(key))
            }
        }
        val say = json.optString("say").ifBlank { json.optString("message") }
        return AgentAction(tool, args, done, say)
    }

    fun extractJson(raw: String): JSONObject? {
        val trimmed = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
        val candidate = fence?.groupValues?.get(1)?.trim() ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(candidate.substring(start, end + 1)) }.getOrNull()
    }

    const val SYSTEM = """
You are MobileForge Agent on the user's phone. You DO the work by calling tools.
Reply with ONE JSON object only, no markdown.
To call a tool: {"tool":"NAME","args":{...}}
When finished: {"done":true,"say":"short summary"}

Tools:
- project.create {"name":"SkyArena","type":"3d"}
- project.list {}
- project.open {"name":"SkyArena"}
- project.seed_demo {}
- fs.list {}
- fs.read {"path":"Scripts/Player.cs"}
- fs.write {"path":"Scripts/Player.cs","content":"..."}
- scene.create {"name":"Main","dimension":"3D"}
- scene.list {}
- scene.add_object {"type":"Player"}
- controls.set {"items":[{"type":"joystick","anchor":"bl","action":"move"},{"type":"button","anchor":"br","label":"Jump","action":"jump"}]}
- play.start {}

Rules:
- You are not the director. Implement the user's order.
- Prefer .cs scripts.
- Do not invent a default touch UI unless asked; then use controls.set.
- After writes, you may fs.read to verify.
- Stop with done when the order is complete.
"""
}
