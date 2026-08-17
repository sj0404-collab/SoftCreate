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
        .put("extra", extra)

    companion object {
        fun fromJson(json: JSONObject): SceneObject = SceneObject(
            name = json.optString("name", "Object"),
            type = json.optString("type", "Empty"),
            x = json.optDouble("x").toFloat(),
            y = json.optDouble("y").toFloat(),
            z = json.optDouble("z").toFloat(),
            rx = json.optDouble("rx").toFloat(),
            ry = json.optDouble("ry").toFloat(),
            rz = json.optDouble("rz").toFloat(),
            sx = json.optDouble("sx", 1.0).toFloat(),
            sy = json.optDouble("sy", 1.0).toFloat(),
            sz = json.optDouble("sz", 1.0).toFloat(),
            asset = json.optString("asset"),
            script = json.optString("script"),
            color = json.optString("color", "#b69cff"),
            solid = json.optBoolean("solid", true),
            speed = json.optDouble("speed").toFloat(),
            extra = json.optJSONObject("extra") ?: JSONObject(),
        )
    }
}

data class GameScene(
    var name: String,
    var dimension: String,
    val objects: MutableList<SceneObject>,
    val file: File,
) {
    fun toJson(): JSONObject {
        val items = org.json.JSONArray()
        objects.forEach { items.put(it.toJson()) }
        return JSONObject()
            .put("format", "mobileforge.scene.v1")
            .put("name", name)
            .put("dimension", dimension)
            .put("objects", items)
    }
}
