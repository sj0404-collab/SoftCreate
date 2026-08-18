package com.mobileforge.mcp

import com.mobileforge.GameScene
import com.mobileforge.ProjectStore
import com.mobileforge.SceneStore
import com.mobileforge.cloud.GitHubService
import com.mobileforge.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class McpServer(val id: String, val name: String, val url: String)
data class McpTool(val name: String, val description: String, val local: Boolean)

class McpHub(
    private val secrets: SecretStore,
    private val prefs: android.content.SharedPreferences,
    private val store: ProjectStore,
    private val scenes: SceneStore,
    private val github: GitHubService,
) {
    fun servers(): List<McpServer> {
        val raw = prefs.getString("mcp_servers", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf(
            McpServer("local", "MobileForge Local Tools", "local://studio"),
            McpServer("loopback", "Termux / 127.0.0.1:8765", "http://127.0.0.1:8765"),
        )
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += McpServer(o.getString("id"), o.optString("name"), o.getString("url"))
        }
        return out
    }

    fun addServer(name: String, url: String, token: String): Result<McpServer> = runCatching {
        require(url.startsWith("https://") || url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
            "Только HTTPS или localhost"
        }
        val id = "mcp_" + System.currentTimeMillis().toString(36)
        if (token.isNotBlank()) secrets.put("mcp_$id", token)
        val arr = JSONArray(prefs.getString("mcp_servers", "[]"))
        arr.put(JSONObject().put("id", id).put("name", name.ifBlank { url }).put("url", url))
        prefs.edit().putString("mcp_servers", arr.toString()).apply()
        McpServer(id, name, url)
    }

    fun builtInTools(): List<McpTool> = listOf(
        McpTool("fs.list", "Список файлов проекта", true),
        McpTool("fs.read", "Прочитать файл", true),
        McpTool("fs.write", "Записать файл (только по команде пользователя)", true),
        McpTool("scene.list", "Список сцен", true),
        McpTool("scene.get", "JSON активной сцены", true),
        McpTool("scene.add_object", "Добавить объект в сцену", true),
        McpTool("assets.list", "Ассеты проекта", true),
        McpTool("project.export", "Экспорт проекта JSON", true),
        McpTool("github.whoami", "Логин активного PAT", true),
        McpTool("github.repos", "Репозитории активного аккаунта", true),
        McpTool("github.create_repo", "Создать репозиторий", true),
        McpTool("github.push_file", "Залить файл в выбранный repo", true),
        McpTool("github.trigger_build", "Запустить workflow на runner", true),
        McpTool("github.runs", "Последние прогоны CI", true),
        McpTool("plugins.list", "Установленные плагины", true),
        McpTool("console.note", "Запись в консоль редактора", true),
    )

    fun callLocal(
        tool: String,
        args: JSONObject,
        projectName: String?,
        scene: GameScene?,
        onLog: (String) -> Unit,
        addObject: (String) -> Unit,
        boundRepo: String,
    ): String {
        val project = projectName?.let { store.find(it) }
        return when (tool) {
            "fs.list" -> {
                val p = project ?: error("Нет проекта")
                JSONArray(store.files(p).map { it.relativePath }).toString(2)
            }
            "fs.read" -> {
                val p = project ?: error("Нет проекта")
                store.resolve(p, args.getString("path")).readText()
            }
            "fs.write" -> {
                val p = project ?: error("Нет проекта")
                store.writeFile(p, args.getString("path"), args.optString("content")).getOrThrow()
                "written ${args.getString("path")}"
            }
            "scene.list" -> {
                val p = project ?: error("Нет проекта")
                JSONArray(scenes.scenes(p).map { it.name }).toString()
            }
            "scene.get" -> scene?.toJson()?.toString(2) ?: "{}"
            "scene.add_object" -> {
                addObject(args.optString("type", "Mesh"))
                "ok"
            }
            "assets.list" -> {
                val p = project ?: error("Нет проекта")
                JSONArray(store.files(p).map { it.relativePath }).toString(2)
            }
            "project.export" -> {
                val p = project ?: error("Нет проекта")
                store.exportBundle(p).toString(2)
            }
            "github.whoami" -> github.whoami().getOrThrow()
            "github.repos" -> JSONArray(github.listRepos().getOrThrow().map { it.fullName }).toString(2)
            "github.create_repo" -> github.createRepo(
                args.getString("name"),
                args.optBoolean("private", false),
                args.optString("description", "MobileForge project"),
            ).getOrThrow().fullName
            "github.push_file" -> {
                val repo = args.optString("repo", boundRepo)
                require(repo.isNotBlank()) { "repo не выбран" }
                github.putFile(repo, args.getString("path"), args.optString("content"), args.optString("message", "studio")).getOrThrow()
                "pushed"
            }
            "github.trigger_build" -> {
                val repo = args.optString("repo", boundRepo)
                github.triggerWorkflow(repo, args.optString("workflow", "android.yml")).getOrThrow()
                "dispatched"
            }
            "github.runs" -> {
                val repo = args.optString("repo", boundRepo)
                JSONArray(github.runs(repo).getOrThrow().map { "${it.name} ${it.status}/${it.conclusion}" }).toString(2)
            }
            "plugins.list" -> JSONArray(listOf("PrimitiveFactory", "BlockKit", "LightingKit", "GitHubCloudBuild", "McpBridge")).toString()
            "console.note" -> {
                onLog(args.optString("text"))
                "logged"
            }
            else -> error("Неизвестный local tool: $tool")
        }
    }

    fun callRemote(server: McpServer, tool: String, args: JSONObject): String {
        val token = secrets.get("mcp_${server.id}") ?: secrets.get("mcp_token")
        val body = JSONObject().put("tool", tool).put("args", args).toString()
        val c = (URL(server.url.trimEnd('/') + "/mcp/call").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) error("MCP HTTP $code: ${text.take(240)}")
        return text
    }
}
