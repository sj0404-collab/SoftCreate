package com.mobileforge.ai

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UserModel(
    val uid: String,
    val providerId: String,
    val id: String,
    val label: String,
    val endpoint: String = "",
    val toolFormat: String = "json",
    val family: String = "",
)

class ModelStore(private val prefs: SharedPreferences) {
    fun list(): MutableList<UserModel> {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        val out = mutableListOf<UserModel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += UserModel(
                uid = o.optString("uid").ifBlank { o.optString("id") + i },
                providerId = o.optString("providerId", "custom"),
                id = o.optString("id"),
                label = o.optString("label"),
                endpoint = o.optString("endpoint"),
                toolFormat = o.optString("toolFormat", "json"),
                family = o.optString("family"),
            )
        }
        return out
    }

    fun save(list: List<UserModel>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("uid", m.uid)
                    .put("providerId", m.providerId)
                    .put("id", m.id)
                    .put("label", m.label)
                    .put("endpoint", m.endpoint)
                    .put("toolFormat", m.toolFormat)
                    .put("family", m.family),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(model: UserModel) {
        val list = list()
        val i = list.indexOfFirst { it.uid == model.uid || it.id == model.id }
        if (i >= 0) list[i] = model else list += model
        save(list)
    }

    fun delete(uid: String) {
        save(list().filter { it.uid != uid && it.id != uid })
    }

    companion object {
        private const val KEY = "user_models_v1"
    }
}

object ToolDialect {
    fun familyOf(id: String): String {
        val s = id.lowercase()
        return when {
            "mimo" in s -> "mimo"
            "laguna" in s -> "laguna"
            "pickle" in s -> "pickle"
            "deepseek" in s -> "deepseek"
            "hy3" in s || "hunyuan" in s -> "hy3"
            "nemotron" in s && "ultra" in s -> "nemotron-ultra"
            "nemotron" in s -> "nemotron"
            "gemini" in s -> "gemini"
            "glm" in s -> "glm"
            "kimi" in s -> "kimi"
            "qwen" in s -> "qwen"
            "claude" in s -> "claude"
            "gpt" in s -> "gpt"
            else -> s.substringAfterLast('/').substringBefore(':').take(16)
        }
    }
}
