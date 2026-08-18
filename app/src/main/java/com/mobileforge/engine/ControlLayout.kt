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

        fun parse(raw: String): ControlLayout {
            if (raw.isBlank()) return EMPTY
            val json = JSONObject(raw)
            val arr = json.optJSONArray("items") ?: return EMPTY
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ControlWidget(
                    type = o.optString("type", "button"),
                    anchor = o.optString("anchor", "br"),
                    label = o.optString("label", o.optString("action", "Act")),
                    action = o.optString("action", "action"),
                )
            }
            return ControlLayout(items)
        }
    }
}
