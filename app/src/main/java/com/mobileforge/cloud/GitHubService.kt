package com.mobileforge.cloud

import com.mobileforge.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import java.util.UUID

data class GhAccount(val id: String, val label: String, var login: String)
data class GhRepo(val fullName: String, val privateRepo: Boolean, val htmlUrl: String)
data class GhRun(val id: Long, val status: String, val conclusion: String, val htmlUrl: String, val name: String)

class GitHubService(
    private val secrets: SecretStore,
    private val prefs: android.content.SharedPreferences,
) {
    var activeId: String
        get() = prefs.getString("gh_active", "").orEmpty()
        set(value) { prefs.edit().putString("gh_active", value).apply() }

    fun accounts(): MutableList<GhAccount> {
        val raw = prefs.getString("gh_accounts", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<GhAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += GhAccount(o.getString("id"), o.optString("label"), o.optString("login"))
        }
        return out
    }

    fun saveAccounts(list: List<GhAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("label", it.label).put("login", it.login))
        }
        prefs.edit().putString("gh_accounts", arr.toString()).apply()
    }

    fun addAccount(label: String, token: String): Result<GhAccount> = runCatching {
        require(token.startsWith("ghp_") || token.startsWith("github_pat_") || token.startsWith("gho_")) {
            "Ожидается GitHub PAT"
        }
        val login = whoami(token).getOrThrow()
        val account = GhAccount(UUID.randomUUID().toString().take(8), label.ifBlank { login }, login)
        secrets.put(tokenKey(account.id), token)
        val list = accounts()
        list += account
        saveAccounts(list)
        if (activeId.isBlank()) activeId = account.id
        account
    }

    fun removeAccount(id: String) {
        secrets.put(tokenKey(id), "")
        saveAccounts(accounts().filter { it.id != id })
        if (activeId == id) activeId = accounts().firstOrNull()?.id.orEmpty()
    }

    fun activeToken(): String? = activeId.ifBlank { null }?.let { secrets.get(tokenKey(it)) }

    fun whoami(token: String = activeToken().orEmpty()): Result<String> = runCatching {
        val json = request("GET", "https://api.github.com/user", token)
        json.getString("login")
    }

    fun listRepos(): Result<List<GhRepo>> = runCatching {
        val json = requestArray("GET", "https://api.github.com/user/repos?per_page=100&sort=updated", token())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            GhRepo(o.getString("full_name"), o.optBoolean("private"), o.optString("html_url"))
        }
    }

    fun createRepo(name: String, privateRepo: Boolean, description: String): Result<GhRepo> = runCatching {
        val body = JSONObject()
            .put("name", name)
            .put("private", privateRepo)
            .put("description", description)
            .put("auto_init", true)
        val json = request("POST", "https://api.github.com/user/repos", token(), body.toString())
        GhRepo(json.getString("full_name"), json.optBoolean("private"), json.optString("html_url"))
    }

    fun putFile(repo: String, path: String, content: String, message: String): Result<Unit> = runCatching {
        val sha = runCatching {
            request("GET", "https://api.github.com/repos/$repo/contents/$path", token()).optString("sha")
        }.getOrNull()
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        if (!sha.isNullOrBlank()) body.put("sha", sha)
        request("PUT", "https://api.github.com/repos/$repo/contents/$path", token(), body.toString())
        Unit
    }

    fun triggerWorkflow(repo: String, workflow: String = "android.yml", ref: String = "main"): Result<Unit> =
        runCatching {
            request(
                "POST",
                "https://api.github.com/repos/$repo/actions/workflows/$workflow/dispatches",
                token(),
                JSONObject().put("ref", ref).toString(),
                okEmpty = true,
            )
            Unit
        }

    data class GhContent(val name: String, val path: String, val type: String, val size: Int)

    fun listContents(repo: String, path: String = ""): Result<List<GhContent>> = runCatching {
        val suffix = if (path.isBlank()) "" else "/${path.trimStart('/')}"
        val text = raw("GET", "https://api.github.com/repos/$repo/contents$suffix", token(), null)
        val arr = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GhContent(o.optString("name"), o.optString("path"), o.optString("type"), o.optInt("size"))
        }
    }

    fun getFileText(repo: String, path: String): Result<String> = runCatching {
        val json = request("GET", "https://api.github.com/repos/$repo/contents/${path.trimStart('/')}", token())
        val content = json.optString("content").replace("\n", "")
        if (json.optString("encoding") == "base64" && content.isNotBlank()) {
            String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        } else error("нет содержимого $path")
    }

    fun deleteFile(repo: String, path: String, message: String): Result<Unit> = runCatching {
        val sha = request("GET", "https://api.github.com/repos/$repo/contents/${path.trimStart('/')}", token()).optString("sha")
        raw(
            "DELETE",
            "https://api.github.com/repos/$repo/contents/${path.trimStart('/')}",
            token(),
            JSONObject().put("message", message).put("sha", sha).toString(),
        )
        Unit
    }

    fun runs(repo: String): Result<List<GhRun>> = runCatching {
        val json = request("GET", "https://api.github.com/repos/$repo/actions/runs?per_page=10", token())
        val arr = json.optJSONArray("workflow_runs") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GhRun(
                o.getLong("id"),
                o.optString("status"),
                o.optString("conclusion"),
                o.optString("html_url"),
                o.optString("name"),
            )
        }
    }

    private fun token(): String = activeToken() ?: error("Нет активного GitHub-аккаунта")

    private fun tokenKey(id: String) = "gh_token_$id"

    private fun request(
        method: String,
        url: String,
        token: String,
        body: String? = null,
        okEmpty: Boolean = false,
    ): JSONObject {
        val text = raw(method, url, token, body)
        if (text.isBlank()) {
            if (okEmpty) return JSONObject()
            error("Пустой ответ GitHub")
        }
        return JSONObject(text)
    }

    private fun requestArray(method: String, url: String, token: String): JSONArray =
        JSONArray(raw(method, url, token, null))

    private fun raw(method: String, url: String, token: String, body: String?): String {
        require(token.isNotBlank()) { "GitHub token пуст" }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "MobileForge-Studio")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
        if (code !in 200..299) error("GitHub HTTP $code: ${text.take(240)}")
        return text
    }
}
