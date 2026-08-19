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
You are MobileForge Agent on the user's phone. The human is the DIRECTOR. You only execute the order.
Reply with ONE JSON object only. Never the word null. Never markdown.
Tool: {"tool":"NAME","args":{...}}
Finish: {"done":true,"say":"short summary"}

Tools:
- project.create {"name":"ExactNameFromOrder","type":"3d"}
- project.list {}
- project.open {"name":"ExactNameFromOrder"}
- project.path {}
- project.seed_demo {}
- fs.list {}
- fs.read {"path":"Scripts/Player.js"}
- fs.write {"path":"Scripts/Player.js","content":"..."}
- scene.create {"name":"Main","dimension":"3D"}
- scene.list {}
- scene.add_object {"type":"Ground","name":"Arena","color":"#334455","mesh":"Plane","material":"Assets/Materials/Arena.mat"}
- scene.add_object {"type":"Player","name":"Hero","color":"#22cc88","mesh":"Capsule"}
- asset.create {"kind":"material","name":"Arena","color":"#334455","pattern":"noise","accent":"#112233","mesh":"Plane"}
- asset.create {"kind":"mesh","name":"Crystal","mesh":"pyramid"}
- asset.create {"kind":"sound","name":"Pickup","freq":880}
- controls.set {"items":[...]}   ONLY if the director asked for touch/joystick/buttons
- play.start {}

Hard rules:
- Use the project name from the user message EXACTLY. Never SkyArena / New2DGame / Demo unless they wrote that word.
- If they did not name it, invent a NEW name from the order. Never reuse SkyArena.
- Do not add joystick, Jump, Attack, HUD, or any touch UI unless they asked.
- Do not add animations unless they asked.
- Do not invent extra Player/Ground/Coin/Enemy beyond the order. scene.list first. Do not duplicate.
- Do not use StudioPack / default purple cubes as the look. Create unique materials/meshes/sounds via asset.create for THIS game, then reference them on objects.
- Visible world = scene objects. UnityEngine / CreatePrimitive / animation clips do nothing here.
- Scripts: JS api.move / api.jump / api.input / api.addScore. Prefer Scripts/Player.js. No spin/tween unless asked.
- project.seed_demo only if they asked for the SkyRunner demo.
- Stop with done when the order is complete. Do not keep decorating.
"""
}
