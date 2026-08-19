package com.mobileforge.agent

import org.json.JSONObject

sealed class AgentEvent {
    abstract val id: Long
    data class User(override val id: Long, val text: String) : AgentEvent()
    data class Assistant(
        override val id: Long,
        val text: String,
        val thinking: String = "",
        val live: Boolean = false,
    ) : AgentEvent()
    data class Tool(
        override val id: Long,
        val title: String,
        val tool: String,
        val args: String,
        val result: String,
        val ms: Long,
        val ok: Boolean,
        val expanded: Boolean = false,
    ) : AgentEvent()
}

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
        val cleaned = raw.replace(Regex("(?:null){2,}"), "")
        val json = extractJson(cleaned) ?: return AgentAction(null, JSONObject(), true, cleaned.trim())
        val done = json.optBoolean("done") || json.optString("tool") == "done"
        val tool = if (json.isNull("tool")) null else json.optString("tool").takeIf { it.isNotBlank() && it != "null" }
        val args = json.optJSONObject("args") ?: JSONObject()
        json.keys().forEach { key ->
            if (key != "tool" && key != "args" && key != "done" && key != "say") {
                if (!args.has(key)) args.put(key, json.get(key))
            }
        }
        val say = listOf("say", "message").firstNotNullOfOrNull { key ->
            if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }.orEmpty()
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
Reply with ONE JSON object only, no markdown, no extra text, never the word null.
To call a tool: {"tool":"NAME","args":{...}}
When finished: {"done":true,"say":"short summary"}

Tools:
- project.create {"name":"SkyArena","type":"3d"}
- project.list {}
- project.open {"name":"SkyArena"}
- project.path {}
- project.seed_demo {}
- fs.list {}
- fs.read {"path":"Scripts/Player.js"}
- fs.write {"path":"Scripts/Player.js","content":"..."}
- scene.create {"name":"Main","dimension":"3D"}
- scene.list {}
- scene.add_object {"type":"Ground"}
- scene.add_object {"type":"Player"}
- scene.add_object {"type":"Coin"}
- controls.set {"items":[{"type":"joystick","anchor":"bl","action":"move"},{"type":"button","anchor":"br","label":"Jump","action":"jump"}]}
- play.start {}

Rules:
- You are not the director. Implement the user's order.
- Visible game = scene objects (Ground, Player, Coin, Mesh). UnityEngine / GameObject.CreatePrimitive does NOTHING here.
- Scripts: JS with api.move / api.jump / api.input / api.addScore. Prefer Scripts/Player.js.
- If asked where the project folder is, call project.path and report the absolute path.
- Do not invent a default touch UI unless asked; then use controls.set.
- After writes, you may fs.read to verify.
- Stop with done when the order is complete.
"""
}
