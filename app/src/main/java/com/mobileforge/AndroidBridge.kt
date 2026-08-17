package com.mobileforge

import android.content.Context
import android.webkit.JavascriptInterface
import com.mobileforge.ai.AiGateway
import com.mobileforge.ai.AiResult
import com.mobileforge.ai.Provider
import com.mobileforge.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class AndroidBridge(
    context: Context,
    private val evalJs: (String) -> Unit,
) {
    private val app = context.applicationContext
    private val projects = ProjectStore(app)
    private val scenes = SceneStore()
    private val secrets = SecretStore(app)
    private val gateway = AiGateway(secrets)
    private val prefs = app.getSharedPreferences("mobileforge.providers", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()

    @JavascriptInterface
    fun version(): String = JSONObject()
        .put("ok", true)
        .put("name", "MobileForge")
        .put("version", BuildConfig.VERSION_NAME)
        .put("native", true)
        .toString()

    @JavascriptInterface
    fun projects(): String = ok(
        JSONArray().apply {
            projects.projects().forEach { project ->
                put(
                    JSONObject()
                        .put("name", project.name)
                        .put("path", project.directory.absolutePath)
                        .put("meta", projects.meta(project)),
                )
            }
        },
    )

    @JavascriptInterface
    fun createProject(rawName: String): String = createProjectTyped(rawName, "3d")

    @JavascriptInterface
    fun createProjectTyped(rawName: String, type: String): String = runCatching {
        val project = projects.create(rawName, type).getOrThrow()
        JSONObject().put("ok", true).put("name", project.name)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun deleteProject(name: String): String = runCatching {
        projects.delete(name).getOrThrow()
        JSONObject().put("ok", true)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun renameProject(oldName: String, newName: String): String = runCatching {
        val project = projects.rename(oldName, newName).getOrThrow()
        JSONObject().put("ok", true).put("name", project.name)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun seedDemo(): String = runCatching {
        val project = projects.seedDemo()
        JSONObject().put("ok", true).put("name", project.name)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun projectMeta(name: String): String = runCatching {
        val project = requireProject(name)
        JSONObject().put("ok", true).put("meta", projects.meta(project))
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun saveProjectMeta(name: String, json: String): String = runCatching {
        val project = requireProject(name)
        projects.saveMeta(project, JSONObject(json))
        JSONObject().put("ok", true)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun listFiles(name: String): String = runCatching {
        val project = requireProject(name)
        val arr = JSONArray()
        projects.files(project).forEach { file ->
            arr.put(
                JSONObject()
                    .put("path", file.relativePath)
                    .put("name", file.file.name)
                    .put("size", file.file.length()),
            )
        }
        JSONObject().put("ok", true).put("files", arr)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun readFile(name: String, path: String): String = runCatching {
        val project = requireProject(name)
        val file = projects.resolve(project, path)
        require(file.isFile) { "File not found." }
        JSONObject().put("ok", true).put("path", path).put("content", file.readText())
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun writeFile(name: String, path: String, content: String): String = runCatching {
        val project = requireProject(name)
        projects.writeFile(project, path, content).getOrThrow()
        JSONObject().put("ok", true).put("path", path)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun createFile(name: String, path: String, content: String): String = runCatching {
        val project = requireProject(name)
        projects.createFile(project, path, content).getOrThrow()
        JSONObject().put("ok", true).put("path", path)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun deleteFile(name: String, path: String): String = runCatching {
        projects.deleteFile(requireProject(name), path).getOrThrow()
        JSONObject().put("ok", true)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun renameFile(name: String, from: String, to: String): String = runCatching {
        projects.renameFile(requireProject(name), from, to).getOrThrow()
        JSONObject().put("ok", true).put("path", to)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun scenes(name: String): String = runCatching {
        val arr = JSONArray()
        scenes.scenes(requireProject(name)).forEach { scene ->
            arr.put(scene.toJson())
        }
        JSONObject().put("ok", true).put("scenes", arr)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun createScene(name: String, sceneName: String, dimension: String): String = runCatching {
        val scene = scenes.create(requireProject(name), sceneName, dimension).getOrThrow()
        JSONObject().put("ok", true).put("scene", scene.toJson())
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun saveScene(name: String, sceneJson: String): String = runCatching {
        val scene = scenes.saveJson(requireProject(name), JSONObject(sceneJson)).getOrThrow()
        JSONObject().put("ok", true).put("scene", scene.toJson())
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun deleteScene(name: String, sceneName: String): String = runCatching {
        scenes.delete(requireProject(name), sceneName).getOrThrow()
        JSONObject().put("ok", true)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun exportProject(name: String): String = runCatching {
        JSONObject().put("ok", true).put("bundle", projects.exportBundle(requireProject(name)))
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun importProject(bundleJson: String): String = runCatching {
        val project = projects.importBundle(JSONObject(bundleJson)).getOrThrow()
        JSONObject().put("ok", true).put("name", project.name)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun saveProvider(id: String, endpoint: String, apiKey: String, model: String): String = runCatching {
        val key = normalizeProvider(id)
        if (key == "custom" && endpoint.isNotBlank() && !endpoint.startsWith("https://")) {
            error("Custom endpoint must use HTTPS")
        }
        if (apiKey.isNotBlank()) secrets.put(secretName(key), apiKey)
        if (key == "custom" && endpoint.isNotBlank()) secrets.put("custom_endpoint", endpoint)
        prefs.edit()
            .putString("${key}_endpoint", endpoint)
            .putString("${key}_model", model)
            .apply()
        JSONObject().put("ok", true)
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun providerConfig(id: String): String = runCatching {
        val key = normalizeProvider(id)
        JSONObject()
            .put("ok", true)
            .put("id", key)
            .put("endpoint", prefs.getString("${key}_endpoint", "") ?: "")
            .put("model", prefs.getString("${key}_model", "") ?: "")
            .put("hasKey", secrets.has(secretName(key)))
    }.fold({ it.toString() }, { fail(it) })

    @JavascriptInterface
    fun generate(providerId: String, model: String, prompt: String, endpoint: String): String {
        return encodeAi(
            runCatching {
                gateway.send(Provider.fromId(providerId), model, prompt, endpoint)
            }.getOrElse { AiResult.Failure(it.message ?: "AI error") },
        )
    }

    @JavascriptInterface
    fun generateAsync(
        requestId: String,
        providerId: String,
        model: String,
        prompt: String,
        endpoint: String,
    ): String {
        io.execute {
            val payload = encodeAi(
                runCatching {
                    gateway.send(Provider.fromId(providerId), model, prompt, endpoint)
                }.getOrElse { AiResult.Failure(it.message ?: "AI error") },
            )
            val safeId = JSONObject.quote(requestId)
            val safePayload = JSONObject.quote(payload)
            evalJs("window.MF_onAi && window.MF_onAi($safeId, JSON.parse($safePayload))")
        }
        return JSONObject().put("ok", true).put("accepted", true).put("id", requestId).toString()
    }

    @JavascriptInterface
    fun checkMcp(): String = encodeAi(gateway.pingMcp())

    @JavascriptInterface
    fun testProvider(id: String, model: String, endpoint: String): String {
        return encodeAi(
            runCatching {
                gateway.send(
                    Provider.fromId(id),
                    model.ifBlank { "ping" },
                    "Reply with the single word PONG.",
                    endpoint,
                )
            }.getOrElse { AiResult.Failure(it.message ?: "Test failed") },
        )
    }

    private fun requireProject(name: String) =
        projects.find(name) ?: error("Project not found.")

    private fun normalizeProvider(id: String): String = when (id.lowercase()) {
        "zen", "zen_direct" -> "zen"
        "openrouter" -> "openrouter"
        "mcp", "local_mcp" -> "mcp"
        "custom" -> "custom"
        else -> error("Unsupported provider")
    }

    private fun secretName(id: String): String = when (id) {
        "zen" -> "zen_key"
        "openrouter" -> "openrouter_key"
        "mcp" -> "mcp_token"
        else -> "custom_key"
    }

    private fun encodeAi(result: AiResult): String = when (result) {
        is AiResult.Success -> JSONObject().put("ok", true).put("text", result.text).toString()
        is AiResult.Failure -> JSONObject().put("ok", false).put("error", result.message).toString()
    }

    private fun ok(data: JSONArray): String = JSONObject().put("ok", true).put("projects", data).toString()

    private fun fail(error: Throwable): String =
        JSONObject().put("ok", false).put("error", error.message ?: "Error").toString()
}
