package com.mobileforge

import org.json.JSONObject
import java.io.File

data class SceneObject(
    var name: String,
    var type: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var rx: Float = 0f,
    var ry: Float = 0f,
    var rz: Float = 0f,
    var sx: Float = 1f,
    var sy: Float = 1f,
    var sz: Float = 1f,
    var asset: String = "",
    var script: String = "",
    var color: String = "#b69cff",
    var solid: Boolean = true,
    var speed: Float = 0f,
    var extra: JSONObject = JSONObject(),
    var mesh: String = "Cube",
    var material: String = "",
    var lightType: String = "Directional",
    var intensity: Float = 1f,
    var fov: Float = 60f,
    var near: Float = 0.3f,
    var far: Float = 200f,
    var tag: String = "Untagged",
    var layer: String = "Default",
    var enabled: Boolean = true,
    var mass: Float = 1f,
    var plugin: String = "",
    var prefab: String = "",
    var parent: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("type", type)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("rx", rx.toDouble())
        .put("ry", ry.toDouble())
        .put("rz", rz.toDouble())
        .put("sx", sx.toDouble())
        .put("sy", sy.toDouble())
        .put("sz", sz.toDouble())
        .put("asset", asset)
        .put("script", script)
        .put("color", color)
        .put("solid", solid)
        .put("speed", speed.toDouble())
        .put("mesh", mesh)
        .put("material", material)
        .put("lightType", lightType)
        .put("intensity", intensity.toDouble())
        .put("fov", fov.toDouble())
        .put("near", near.toDouble())
        .put("far", far.toDouble())
        .put("tag", tag)
        .put("layer", layer)
        .put("enabled", enabled)
        .put("mass", mass.toDouble())
        .put("plugin", plugin)
        .put("prefab", prefab)
        .put("parent", parent)
        .put("extra", extra)

    companion object {
        val MESHES = listOf("Cube", "Sphere", "Cylinder", "Capsule", "Plane", "Quad", "Block", "Wedge", "Empty")
        val LIGHTS = listOf("Directional", "Point", "Spot")
        val TYPES = listOf(
            "Camera", "Player", "Mesh", "Sprite", "Ground", "Light",
            "Coin", "Enemy", "Empty", "Button", "Block", "Prefab",
            "Npc", "Particle", "Canvas", "Terrain", "Trigger", "Pawn",
            "Character", "Rigidbody", "Audio", "Nav",
        )

        private fun num(json: JSONObject, key: String, def: Double): Float {
            val v = json.optDouble(key, def)
            return if (v.isFinite()) v.toFloat() else def.toFloat()
        }

        fun fromJson(json: JSONObject): SceneObject = SceneObject(
            name = json.optString("name", "Object").let { if (it == "null") "Object" else it },
            type = json.optString("type", "Empty").let { if (it == "null") "Empty" else it },
            x = num(json, "x", 0.0),
            y = num(json, "y", 0.0),
            z = num(json, "z", 0.0),
            rx = num(json, "rx", 0.0),
            ry = num(json, "ry", 0.0),
            rz = num(json, "rz", 0.0),
            sx = num(json, "sx", 1.0),
            sy = num(json, "sy", 1.0),
            sz = num(json, "sz", 1.0),
            asset = json.optString("asset"),
            script = json.optString("script"),
            color = json.optString("color", "#b69cff"),
            solid = json.optBoolean("solid", true),
            speed = json.optDouble("speed").toFloat(),
            extra = json.optJSONObject("extra") ?: JSONObject(),
            mesh = json.optString("mesh", defaultMesh(json.optString("type"))),
            material = json.optString("material"),
            lightType = json.optString("lightType", "Directional"),
            intensity = json.optDouble("intensity", 1.0).toFloat(),
            fov = json.optDouble("fov", 60.0).toFloat(),
            near = json.optDouble("near", 0.3).toFloat(),
            far = json.optDouble("far", 200.0).toFloat(),
            tag = json.optString("tag", "Untagged"),
            layer = json.optString("layer", "Default"),
            enabled = json.optBoolean("enabled", true),
            mass = json.optDouble("mass", 1.0).toFloat(),
            plugin = json.optString("plugin"),
            prefab = json.optString("prefab"),
        )

        fun defaultMesh(type: String): String = when (type) {
            "Sphere", "Coin" -> "Sphere"
            "Ground", "Plane" -> "Plane"
            "Block" -> "Block"
            "Light", "Camera", "Empty" -> "Empty"
            "Capsule", "Player", "Npc", "NPC", "Human", "Villager" -> "Capsule"
            else -> "Cube"
        }
    }
}

data class GameScene(
    var name: String,
    var dimension: String,
    val objects: MutableList<SceneObject>,
    val file: File,
    var gravity: Float = 16f,
    var skyTop: String = "#15202B",
    var skyBottom: String = "#0B0D14",
    var ambient: Float = 0.35f,
    var timeScale: Float = 1f,
) {
    fun toJson(): JSONObject {
        val items = org.json.JSONArray()
        objects.forEach { items.put(it.toJson()) }
        return JSONObject()
            .put("format", "mobileforge.scene.v2")
            .put("name", name)
            .put("dimension", dimension)
            .put("gravity", gravity.toDouble())
            .put("skyTop", skyTop)
            .put("skyBottom", skyBottom)
            .put("ambient", ambient.toDouble())
            .put("timeScale", timeScale.toDouble())
            .put("objects", items)
    }
}
