package com.mobileforge.engine

import org.json.JSONArray
import org.json.JSONObject

data class GraphNode(
    val id: String,
    var kind: String,
    var x: Float = 0f,
    var y: Float = 0f,
    val args: JSONObject = JSONObject(),
)

data class GraphLink(val from: String, val to: String)

data class VisualGraph(
    var name: String,
    val nodes: MutableList<GraphNode> = mutableListOf(),
    val links: MutableList<GraphLink> = mutableListOf(),
) {
    fun toJson(): JSONObject {
        val ns = JSONArray()
        nodes.forEach { n ->
            ns.put(JSONObject().put("id", n.id).put("kind", n.kind).put("x", n.x.toDouble()).put("y", n.y.toDouble()).put("args", n.args))
        }
        val ls = JSONArray()
        links.forEach { l -> ls.put(JSONObject().put("from", l.from).put("to", l.to)) }
        return JSONObject().put("format", "mobileforge.graph.v1").put("name", name).put("nodes", ns).put("links", ls)
    }

    companion object {
        val KINDS = listOf(
            "OnStart", "OnUpdate", "OnFixed", "OnCollision", "OnTrigger",
            "InputMove", "IfJump", "IfAction", "IfRaycast",
            "Move", "Jump", "AddForce", "SetPosition", "LookAt",
            "Destroy", "Spawn", "PlaySound", "AddScore", "SetColor", "Emit",
            "ReplaceKind",
        )

        fun parse(raw: String): VisualGraph {
            val json = JSONObject(raw)
            val g = VisualGraph(json.optString("name", "Graph"))
            val ns = json.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until ns.length()) {
                val o = ns.optJSONObject(i) ?: continue
                g.nodes += GraphNode(
                    o.optString("id", "n$i"),
                    o.optString("kind", "OnUpdate"),
                    o.optDouble("x").toFloat(),
                    o.optDouble("y").toFloat(),
                    o.optJSONObject("args") ?: JSONObject(),
                )
            }
            val ls = json.optJSONArray("links") ?: JSONArray()
            for (i in 0 until ls.length()) {
                val o = ls.optJSONObject(i) ?: continue
                g.links += GraphLink(o.optString("from"), o.optString("to"))
            }
            return g
        }

        fun playerDefault(): VisualGraph {
            val g = VisualGraph("Player")
            g.nodes += GraphNode("u", "OnUpdate", 20f, 20f)
            g.nodes += GraphNode("m", "InputMove", 20f, 90f)
            g.nodes += GraphNode("mv", "Move", 180f, 90f, JSONObject().put("speed", 6))
            g.nodes += GraphNode("j", "IfJump", 20f, 170f)
            g.nodes += GraphNode("jp", "Jump", 180f, 170f, JSONObject().put("force", 7))
            g.links += GraphLink("u", "m")
            g.links += GraphLink("m", "mv")
            g.links += GraphLink("u", "j")
            g.links += GraphLink("j", "jp")
            return g
        }
    }
}

object GraphRunner {
    fun run(graph: VisualGraph, event: String, actor: Actor, rt: GameRuntime, dt: Float, other: Actor? = null) {
        val want = when (event) {
            "onStart" -> "OnStart"
            "onUpdate" -> "OnUpdate"
            "onFixedUpdate" -> "OnFixed"
            "onCollisionEnter" -> "OnCollision"
            "onTriggerEnter" -> "OnTrigger"
            else -> return
        }
        val kids = HashMap<String, MutableList<String>>()
        graph.links.forEach { kids.getOrPut(it.from) { mutableListOf() } += it.to }
        val map = graph.nodes.associateBy { it.id }
        graph.nodes.filter { it.kind == want }.forEach { walk(it, kids, map, actor, rt, dt, other) }
    }

    private fun walk(
        node: GraphNode,
        kids: Map<String, List<String>>,
        map: Map<String, GraphNode>,
        actor: Actor,
        rt: GameRuntime,
        dt: Float,
        other: Actor?,
    ) {
        if (!exec(node, actor, rt, dt, other)) return
        kids[node.id].orEmpty().forEach { id ->
            map[id]?.let { walk(it, kids, map, actor, rt, dt, other) }
        }
    }

    private fun exec(node: GraphNode, actor: Actor, rt: GameRuntime, dt: Float, other: Actor?): Boolean {
        val a = node.args
        return when (node.kind) {
            "OnStart", "OnUpdate", "OnFixed", "OnCollision", "OnTrigger" -> true
            "InputMove" -> {
                actor.x += rt.input.x * a.optDouble("speed", 6.0).toFloat() * dt
                if (rt.dimension.equals("2D", true)) actor.y += rt.input.y * a.optDouble("speed", 6.0).toFloat() * dt
                else actor.z += -rt.input.y * a.optDouble("speed", 6.0).toFloat() * dt
                true
            }
            "IfJump" -> rt.input.jump
            "IfAction" -> rt.input.action
            "IfRaycast" -> rt.raycast(actor, a.optDouble("dist", 4.0).toFloat(), a.optString("layer")) != null
            "Move" -> {
                val s = a.optDouble("speed", 6.0).toFloat()
                actor.x += rt.input.x * s * dt
                if (rt.dimension.equals("2D", true)) actor.y += rt.input.y * s * dt
                else actor.z += -rt.input.y * s * dt
                true
            }
            "Jump" -> {
                actor.vy = a.optDouble("force", 7.0).toFloat(); true
            }
            "AddForce" -> {
                actor.vx += a.optDouble("x").toFloat()
                actor.vy += a.optDouble("y").toFloat()
                actor.vz += a.optDouble("z").toFloat()
                true
            }
            "SetPosition" -> {
                actor.x = a.optDouble("x", actor.x.toDouble()).toFloat()
                actor.y = a.optDouble("y", actor.y.toDouble()).toFloat()
                actor.z = a.optDouble("z", actor.z.toDouble()).toFloat()
                true
            }
            "LookAt" -> {
                val t = rt.actors.find { it.alive && it.name == a.optString("target") } ?: return false
                actor.ry = Math.toDegrees(kotlin.math.atan2((t.x - actor.x).toDouble(), (t.z - actor.z).toDouble())).toFloat()
                true
            }
            "Destroy" -> {
                val n = a.optString("name").ifBlank { other?.name ?: actor.name }
                rt.actors.find { it.name == n }?.alive = false
                true
            }
            "Spawn" -> {
                rt.instantiate(a.optString("prefab").ifBlank { "Mesh" }, actor.x, actor.y, actor.z)
                true
            }
            "PlaySound" -> { rt.playSound(a.optString("clip")); true }
            "AddScore" -> { rt.score += a.optInt("n", 1); true }
            "SetColor" -> { actor.color = a.optString("color", actor.color); true }
            "Emit" -> { rt.signals.emit(a.optString("name", "tick")); true }
            else -> true
        }
    }
}
