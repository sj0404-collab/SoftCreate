package com.mobileforge.ai

import org.json.JSONArray
import org.json.JSONObject

object StreamText {
    fun clean(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        if (raw == "null" || raw == "undefined" || raw == "None" || raw == "nil") return ""
        return raw
    }

    fun stripNullSpam(raw: String): String =
        raw.replace(Regex("(?:null){2,}"), "").trim()

    fun jsonText(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val piece = fromValue(obj.opt(key))
            if (piece.isNotEmpty()) return piece
        }
        return ""
    }

    fun fromValue(value: Any?): String {
        if (value == null || value == JSONObject.NULL) return ""
        return when (value) {
            is String -> clean(value)
            is JSONArray -> buildString {
                for (i in 0 until value.length()) {
                    val piece = fromValue(value.opt(i))
                    if (piece.isNotEmpty()) append(piece)
                }
            }
            is JSONObject -> jsonText(value, "text", "content", "value")
            is Number, is Boolean -> value.toString()
            else -> clean(value.toString())
        }
    }

    fun deltaOf(choice: JSONObject): Pair<String, String> {
        val delta = choice.optJSONObject("delta")
            ?: choice.optJSONObject("message")
            ?: JSONObject()
        val content = jsonText(delta, "content")
        val reason = jsonText(delta, "reasoning", "reasoning_content", "thinking", "reasoning_text")
        return content to reason
    }

    fun humanError(error: Throwable): String {
        val message = error.message.orEmpty()
        val low = message.lowercase()
        return when {
            "api key is not configured" in low || "key is not configured" in low ->
                "Нужен ключ в Ещё → Save securely. OpenRouter :free тоже требует ключ (хватит бесплатного аккаунта)."
            hostDead(error) ->
                "Нет сети/DNS до провайдера. Интернет или VPN, затем другая модель в чипе."
            "failed to connect" in low || "econnrefused" in low || "timed out" in low || "timeout" in low ->
                "Провайдер не отвечает. Выберите другую модель."
            "429" in message || "freeusagelimit" in low || "rate limit" in low || "quota" in low ->
                "Лимит бесплатных запросов. Смените модель в чипе (Zen / OpenRouter :free / Orca)."
            "modelerror" in low || ("http 401" in low && "model" in low) || "is not available" in low ->
                "Эта модель сейчас выключена у провайдера. Возьмите другую в чипе."
            "http 403" in low ->
                "Доступ запрещён (ключ или модель). Проверьте ключ в Ещё."
            "http 401" in low ->
                "Ключ отклонён. Проверьте в Ещё."
            "http 502" in message || "http 503" in message || "http 504" in message ||
                "unavailable" in low || "upstream" in low ->
                "Апстрим провайдера лёг (502/503). Агент сам переключит модель."
            else -> message.replace(Regex("\\s+"), " ").take(280)
        }
    }

    fun retryable(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        val markers = listOf(
            "502", "503", "504", "429", "unavailable", "timeout", "timed out",
            "upstream", "unable to resolve host", "unknownhost", "no address",
            "failed to connect", "modelerror", "not available", "quota",
            "freeusagelimit", "overloaded", "capacity",
        )
        if (markers.any { it in message }) return true
        if ("401" in message && ("model" in message || "not found" in message)) return true
        if ("404" in message) return true
        return false
    }

    fun hostDead(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf(
            "unable to resolve host",
            "unknownhost",
            "no address associated",
            "failed to connect",
            "network is unreachable",
        ).any { it in message }
    }

    fun badKey(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        if ("modelerror" in message || ("model" in message && "401" in message)) return false
        return ("401" in message || "403" in message) &&
            listOf("key", "auth", "unauthorized", "invalid", "forbidden", "permission", "blocked").any { it in message }
    }
}
