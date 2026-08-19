package com.mobileforge.ai

import com.mobileforge.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AiGateway(private val secrets: SecretStore) {
    fun send(
        provider: Provider,
        model: String,
        prompt: String,
        customEndpoint: String = "",
    ): AiResult = converse(
        provider,
        model,
        listOf(ChatTurn("user", prompt)),
        customEndpoint,
    ).fold(
        { AiResult.Success(it.text) },
        { AiResult.Failure(it.message ?: "AI error") },
    )

    fun converse(
        provider: Provider,
        model: String,
        messages: List<ChatTurn>,
        customEndpoint: String = "",
    ): Result<ChatReply> = runCatching {
        when (provider) {
            Provider.ZEN_DIRECT -> chat(
                endpoint = "https://opencode.ai/zen/v1/chat/completions",
                key = secrets.get("zen_key"),
                model = model,
                messages = messages,
                title = "Zen",
                requireKey = false,
            )
            Provider.OPENROUTER -> chat(
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                key = secrets.get("openrouter_key"),
                model = model,
                messages = messages,
                title = "OpenRouter",
            )
            Provider.LOCAL_MCP -> {
                val joined = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                when (val r = mcp(joined)) {
                    is AiResult.Success -> ChatReply(r.text)
                    is AiResult.Failure -> error(r.message)
                }
            }
            Provider.CUSTOM -> {
                val endpoint = customEndpoint.ifBlank { secrets.get("custom_endpoint").orEmpty() }
                require(endpoint.startsWith("https://")) { "Custom endpoint must use HTTPS" }
                chat(endpoint, secrets.get("custom_key"), model, messages, "Custom")
            }
        }
    }

    fun pingMcp(): AiResult {
        val token = secrets.get("mcp_token")
            ?: return AiResult.Failure("Set a local MCP token first")
        return try {
            val health = get("http://127.0.0.1:8765/health", "Bearer $token")
            AiResult.Success(health.ifBlank { "MCP is reachable" })
        } catch (_: Exception) {
            try {
                mcp("ping")
            } catch (e: Exception) {
                AiResult.Failure(e.message ?: "MCP is not reachable on 127.0.0.1:8765")
            }
        }
    }

    private fun chat(
        endpoint: String,
        key: String?,
        model: String,
        messages: List<ChatTurn>,
        title: String,
        requireKey: Boolean = true,
    ): ChatReply {
        if (requireKey && key.isNullOrBlank()) {
            error("$title API key is not configured")
        }
        val arr = JSONArray()
        messages.forEach { turn ->
            arr.put(JSONObject().put("role", turn.role).put("content", turn.content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", arr)
            .put("stream", false)
            .toString()
        val auth = if (key.isNullOrBlank()) null else "Bearer $key"
        val response = post(endpoint, body, auth)
        val json = JSONObject(response)
        if (json.has("error")) {
            val error = json.optJSONObject("error")
            val message = error?.optString("message").orEmpty().ifBlank {
                json.optString("error", "Provider error")
            }
            error(message)
        }
        val text = json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content", "Empty response")
        val usage = json.optJSONObject("usage")
        return ChatReply(
            text = text,
            promptTokens = usage?.optInt("prompt_tokens") ?: 0,
            completionTokens = usage?.optInt("completion_tokens") ?: 0,
        )
    }

    private fun mcp(prompt: String): AiResult {
        val token = secrets.get("mcp_token")
            ?: return AiResult.Failure("Set a local MCP token first")
        val body = JSONObject()
            .put("tool", "subagent_task")
            .put(
                "args",
                JSONObject().put("agent", "general").put("prompt", prompt),
            )
            .toString()
        val response = post("http://127.0.0.1:8765/mcp/call", body, "Bearer $token")
        val json = JSONObject(response)
        if (!json.optBoolean("success", json.has("result"))) {
            return AiResult.Failure(json.optString("error", "MCP request failed"))
        }
        val result = json.optJSONObject("result")
        val output = result?.optString("output")
            ?: json.opt("result")?.toString()
            ?: ""
        return AiResult.Success(output)
    }

    private fun post(endpoint: String, body: String, authorization: String?): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (!authorization.isNullOrBlank()) {
                setRequestProperty("Authorization", authorization)
            }
            setRequestProperty("Accept", "application/json")
            if (endpoint.contains("openrouter.ai")) {
                setRequestProperty("HTTP-Referer", "https://mobileforge.local")
                setRequestProperty("X-Title", "MobileForge")
            }
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return read(connection)
    }

    private fun get(endpoint: String, authorization: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", authorization)
            setRequestProperty("Accept", "application/json")
        }
        return read(connection)
    }

    private fun read(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${text.take(400)}")
        }
        return text
    }
}
