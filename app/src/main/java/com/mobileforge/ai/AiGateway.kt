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
            Provider.ZEN_DIRECT -> zenChat(model, messages)
            Provider.OPENROUTER -> chat(
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                key = secrets.get("openrouter_key"),
                model = ModelCatalog.remap(Provider.OPENROUTER, model),
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
            Provider.ORCA -> chat(
                endpoint = "https://api.orcarouter.ai/v1/chat/completions",
                key = secrets.get("orca_key"),
                model = ModelCatalog.remap(Provider.ORCA, model),
                messages = messages,
                title = "Orca",
            )
            Provider.GEMINI -> geminiChat(model, messages)
            Provider.CUSTOM -> {
                val endpoint = customEndpoint.ifBlank { secrets.get("custom_endpoint").orEmpty() }
                require(endpoint.startsWith("https://")) { "Custom endpoint must use HTTPS" }
                chat(endpoint, secrets.get("custom_key"), model, messages, "Custom")
            }
        }
    }

    private fun zenChat(preferred: String, messages: List<ChatTurn>): ChatReply {
        val queue = if (preferred == "auto" || preferred.isBlank()) {
            ModelCatalog.zenFreeIds()
        } else {
            (listOf(ModelCatalog.remap(Provider.ZEN_DIRECT, preferred)) + ModelCatalog.zenFreeIds()).distinct()
        }
        var last: Exception? = null
        for (candidate in queue) {
            var nextModel = false
            for (attempt in 0 until 2) {
                try {
                    return chat(
                        endpoint = "https://opencode.ai/zen/v1/chat/completions",
                        key = secrets.get("zen_key"),
                        model = candidate,
                        messages = messages,
                        title = "Zen",
                        requireKey = false,
                    ).copy(model = candidate, provider = "zen")
                } catch (e: Exception) {
                    last = e
                    if (StreamText.hostDead(e)) throw e
                    if (!StreamText.retryable(e)) {
                        nextModel = true
                        break
                    }
                    Thread.sleep(500L * (attempt + 1))
                }
            }
            if (nextModel) continue
        }
        throw last ?: IllegalStateException("Zen недоступен")
    }

    fun converseResilient(
        preferred: Provider,
        model: String,
        messages: List<ChatTurn>,
        customEndpoint: String = "",
    ): Result<ChatReply> {
        val routes = ModelCatalog.plan(preferred, model, ::hasProvider)
        var last: Throwable? = null
        val skip = mutableSetOf<Provider>()
        for ((provider, id) in routes) {
            if (provider in skip) continue
            val result = converse(provider, id, messages, customEndpoint)
            if (result.isSuccess) {
                return result.map {
                    it.copy(
                        model = it.model.ifBlank { id },
                        provider = ModelCatalog.idOf(provider),
                    )
                }
            }
            last = result.exceptionOrNull()
            if (last != null && (StreamText.hostDead(last) || StreamText.badKey(last))) {
                skip += provider
            }
        }
        return Result.failure(last ?: IllegalStateException("Все провайдеры недоступны"))
    }

    fun converseStream(
        provider: Provider,
        model: String,
        messages: List<ChatTurn>,
        customEndpoint: String = "",
        abort: () -> Boolean = { false },
        onDelta: (StreamDelta) -> Unit,
    ): Result<ChatReply> {
        val routes = ModelCatalog.plan(provider, model, ::hasProvider)
        var last: Throwable? = null
        val skip = mutableSetOf<Provider>()
        var started = false
        for ((routeProvider, routeModel) in routes) {
            if (abort()) break
            if (routeProvider in skip) continue
            if (started) onDelta(StreamDelta(reset = true))
            started = true
            val streamed = runCatching {
                when (routeProvider) {
                    Provider.GEMINI -> geminiStream(routeModel, messages, abort, onDelta)
                    Provider.LOCAL_MCP -> converse(routeProvider, routeModel, messages, customEndpoint).getOrThrow()
                    else -> {
                        val route = endpointFor(routeProvider, routeModel, customEndpoint)
                        streamChat(route.endpoint, route.key, route.model, messages, route.requireKey, abort, onDelta)
                    }
                }
            }
            if (streamed.isSuccess) {
                val reply = streamed.getOrThrow()
                val text = StreamText.stripNullSpam(reply.text)
                val think = StreamText.stripNullSpam(reply.thinking)
                if (text.isNotBlank() || think.isNotBlank()) {
                    return Result.success(
                        reply.copy(
                            text = text,
                            thinking = think,
                            model = routeModel,
                            provider = ModelCatalog.idOf(routeProvider),
                        ),
                    )
                }
                val plain = converse(routeProvider, routeModel, messages, customEndpoint)
                if (plain.isSuccess) {
                    return plain.map {
                        it.copy(model = routeModel, provider = ModelCatalog.idOf(routeProvider))
                    }
                }
                last = plain.exceptionOrNull() ?: IllegalStateException("empty stream")
            } else {
                last = streamed.exceptionOrNull()
                val err = last
                if (err != null && (StreamText.hostDead(err) || StreamText.badKey(err))) {
                    skip += routeProvider
                }
                val msg = err?.message.orEmpty().lowercase()
                if ("stream" in msg && ("400" in msg || "unsupported" in msg)) {
                    val plain = converse(routeProvider, routeModel, messages, customEndpoint)
                    if (plain.isSuccess) {
                        return plain.map {
                            it.copy(model = routeModel, provider = ModelCatalog.idOf(routeProvider))
                        }
                    }
                    last = plain.exceptionOrNull() ?: err
                }
            }
        }
        return Result.failure(last ?: IllegalStateException("Все провайдеры недоступны"))
    }

    private fun hasProvider(provider: Provider): Boolean = when (provider) {
        Provider.ZEN_DIRECT -> true
        Provider.OPENROUTER -> !secrets.get("openrouter_key").isNullOrBlank()
        Provider.ORCA -> !secrets.get("orca_key").isNullOrBlank()
        Provider.GEMINI -> geminiKeys().isNotEmpty()
        Provider.CUSTOM -> !secrets.get("custom_key").isNullOrBlank()
        Provider.LOCAL_MCP -> !secrets.get("mcp_token").isNullOrBlank()
    }

    private data class Route(val endpoint: String, val key: String?, val requireKey: Boolean, val model: String)

    private fun endpointFor(provider: Provider, model: String, customEndpoint: String): Route = when (provider) {
        Provider.ZEN_DIRECT -> Route(
            "https://opencode.ai/zen/v1/chat/completions",
            secrets.get("zen_key"),
            false,
            ModelCatalog.remap(Provider.ZEN_DIRECT, model),
        )
        Provider.OPENROUTER -> Route(
            "https://openrouter.ai/api/v1/chat/completions",
            secrets.get("openrouter_key"),
            true,
            ModelCatalog.remap(Provider.OPENROUTER, model),
        )
        Provider.ORCA -> Route(
            "https://api.orcarouter.ai/v1/chat/completions",
            secrets.get("orca_key"),
            true,
            ModelCatalog.remap(Provider.ORCA, model),
        )
        Provider.CUSTOM -> Route(
            customEndpoint.ifBlank { secrets.get("custom_endpoint").orEmpty() },
            secrets.get("custom_key"),
            true,
            model,
        )
        else -> Route("", null, true, model)
    }

    private fun streamChat(
        endpoint: String,
        key: String?,
        model: String,
        messages: List<ChatTurn>,
        requireKey: Boolean,
        abort: () -> Boolean,
        onDelta: (StreamDelta) -> Unit,
    ): ChatReply {
        if (requireKey && key.isNullOrBlank()) error("API key is not configured")
        val arr = JSONArray()
        messages.forEach { turn ->
            arr.put(JSONObject().put("role", turn.role).put("content", turn.content))
        }
        val body = JSONObject().put("model", model).put("messages", arr).put("stream", true).toString()
        val auth = if (key.isNullOrBlank()) null else "Bearer $key"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 16_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (auth != null) setRequestProperty("Authorization", auth)
            if (endpoint.contains("openrouter.ai")) {
                setRequestProperty("HTTP-Referer", "https://mobileforge.local")
                setRequestProperty("X-Title", "MobileForge")
            }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                error("HTTP $code: ${err.take(400)}")
            }
            val text = StringBuilder()
            val think = StringBuilder()
            var promptTokens = 0
            var completionTokens = 0
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (abort()) break
                    val line = reader.readLine() ?: break
                    val data = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        line.startsWith("{") -> line.trim()
                        else -> continue
                    }
                    if (data.isEmpty() || data == "[DONE]") {
                        if (data == "[DONE]") break
                        continue
                    }
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    json.optJSONObject("usage")?.let { usage ->
                        promptTokens = usage.optInt("prompt_tokens", promptTokens)
                        completionTokens = usage.optInt("completion_tokens", completionTokens)
                    }
                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val (piece, reason) = StreamText.deltaOf(choice)
                    if (piece.isNotEmpty()) text.append(piece)
                    if (reason.isNotEmpty()) think.append(reason)
                    if (piece.isNotEmpty() || reason.isNotEmpty()) {
                        onDelta(StreamDelta(piece, reason))
                    }
                    val finish = StreamText.clean(choice.optString("finish_reason"))
                    if (finish == "stop" || finish == "end_turn") {
                        // keep reading until [DONE] so usage can arrive
                    }
                }
            }
            return ChatReply(
                text = StreamText.stripNullSpam(text.toString()),
                thinking = StreamText.stripNullSpam(think.toString()),
                model = model,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun geminiStream(
        preferredModel: String,
        messages: List<ChatTurn>,
        abort: () -> Boolean,
        onDelta: (StreamDelta) -> Unit,
    ): ChatReply {
        val model = if (preferredModel.startsWith("gemini")) preferredModel else "gemini-2.5-flash"
        val key = geminiKeys().firstOrNull() ?: error("Gemini API key is not configured")
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val contents = JSONArray()
        messages.filter { it.role != "system" }.forEach { turn ->
            val role = if (turn.role == "assistant") "model" else "user"
            contents.put(
                JSONObject().put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.content))),
            )
        }
        val body = JSONObject().put("contents", contents)
        if (system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 16_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                error("HTTP $code: ${err.take(400)}")
            }
            val text = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (abort()) break
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    val parts = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val piece = StreamText.jsonText(parts.getJSONObject(i), "text")
                        if (piece.isNotEmpty()) {
                            text.append(piece)
                            onDelta(StreamDelta(text = piece))
                        }
                    }
                }
            }
            return ChatReply(text = StreamText.stripNullSpam(text.toString()), model = model, provider = "gemini")
        } finally {
            connection.disconnect()
        }
    }

    private fun geminiKeys(): List<String> = listOf("gemini_key_1", "gemini_key_2", "gemini_key_3")
        .mapNotNull { secrets.get(it)?.takeIf { k -> k.isNotBlank() } }

    private fun geminiChat(preferredModel: String, messages: List<ChatTurn>): ChatReply {
        val models = listOf(
            preferredModel.takeIf { it.startsWith("gemini") },
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
        ).filterNotNull().distinct()
        val keys = geminiKeys()
        require(keys.isNotEmpty()) { "Gemini API key is not configured" }
        var last: Exception? = null
        for (key in keys) {
            for (m in models) {
                try {
                    return geminiOnce(m, key, messages).copy(model = m, provider = "gemini")
                } catch (e: Exception) {
                    last = e
                }
            }
        }
        throw last ?: IllegalStateException("Gemini недоступен")
    }

    private fun geminiOnce(model: String, key: String, messages: List<ChatTurn>): ChatReply {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val contents = JSONArray()
        messages.filter { it.role != "system" }.forEach { turn ->
            val role = if (turn.role == "assistant") "model" else "user"
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.content))),
            )
        }
        val body = JSONObject().put("contents", contents)
        if (system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val response = post(url, body.toString(), null)
        val json = JSONObject(response)
        if (json.has("error")) {
            error(json.optJSONObject("error")?.optString("message") ?: "Gemini error")
        }
        val parts = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .optJSONArray("parts") ?: JSONArray()
        val text = buildString {
            for (i in 0 until parts.length()) append(StreamText.jsonText(parts.getJSONObject(i), "text"))
        }.ifBlank { "Empty response" }
        val usage = json.optJSONObject("usageMetadata")
        return ChatReply(
            text = text,
            promptTokens = usage?.optInt("promptTokenCount") ?: 0,
            completionTokens = usage?.optInt("candidatesTokenCount") ?: 0,
        )
    }

    companion object {
        val ZEN_FREE = ModelCatalog.zenFreeIds()

        fun isTransient(e: Throwable): Boolean = StreamText.retryable(e)
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
            val message = error?.let { StreamText.jsonText(it, "message") }.orEmpty().ifBlank {
                StreamText.clean(json.optString("error")).ifBlank { "Provider error" }
            }
            error(message)
        }
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val text = StreamText.jsonText(message, "content").ifBlank { "Empty response" }
        val thinking = StreamText.jsonText(message, "reasoning", "reasoning_content", "thinking")
        val usage = json.optJSONObject("usage")
        return ChatReply(
            text = StreamText.stripNullSpam(text),
            thinking = StreamText.stripNullSpam(thinking),
            promptTokens = usage?.optInt("prompt_tokens") ?: 0,
            completionTokens = usage?.optInt("completion_tokens") ?: 0,
            model = model,
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
            connectTimeout = 16_000
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
