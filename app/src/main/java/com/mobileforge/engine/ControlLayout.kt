package com.mobileforge.engine

import org.json.JSONObject

data class ControlWidget(
    val type: String,
    val anchor: String,
    val label: String,
    val action: String,
)

data class ControlLayout(val items: List<ControlWidget>) {
    companion object {
        val EMPTY = ControlLayout(emptyList())

        fun defaultPad(): ControlLayout = ControlLayout(
            listOf(
                ControlWidget("joystick", "bl", "Ход", "move"),
                ControlWidget("button", "br", "Прыжок", "jump"),
                ControlWidget("button", "tr", "Действие", "action"),
            ),
        )

        fun toJson(layout: ControlLayout): String {
            val arr = org.json.JSONArray()
            layout.items.forEach { w ->
                arr.put(JSONObject().put("type", w.type).put("anchor", w.anchor).put("label", w.label).put("action", w.action))
            }
            return JSONObject().put("format", "mobileforge.controls.v1").put("items", arr).toString(2)
        }

        fun parse(raw: String): ControlLayout {
            if (raw.isBlank()) return EMPTY
            val trimmed = raw.trim()
            val arr = when {
                trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
                else -> {
                    val json = JSONObject(trimmed)
                    json.optJSONArray("items") ?: json.optJSONArray("controls") ?: json.optJSONArray("widgets")
                }
            } ?: return EMPTY
            val items = (0 until arr.length()).mapNotNull { i ->
                val any = arr.opt(i) ?: return@mapNotNull null
                val o = any as? JSONObject ?: return@mapNotNull null
                val type = o.optString("type", o.optString("kind", "button")).ifBlank { "button" }
                ControlWidget(
                    type = type,
                    anchor = o.optString("anchor", if ("joy" in type.lowercase()) "bl" else "br"),
                    label = o.optString("label", o.optString("action", "Act")),
                    action = o.optString("action", if ("joy" in type.lowercase()) "move" else "action"),
                )
            }
            return ControlLayout(items)
        }
    }
}
