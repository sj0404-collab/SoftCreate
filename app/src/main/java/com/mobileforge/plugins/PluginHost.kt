package com.mobileforge.plugins

import com.mobileforge.ProjectStore
import com.mobileforge.engine.ScriptInterpreter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PluginMenu(val id: String, val label: String)

data class PluginRecord(
    val id: String,
    val title: String,
    val enabled: Boolean,
    val entry: String,
    val hooks: List<String>,
    val menus: List<PluginMenu>,
    val directory: File,
    val summary: String,
)

class PluginHost {
    val loaded = mutableListOf<PluginRecord>()

    fun reload(project: ProjectStore.Project): List<PluginRecord> {
        loaded.clear()
        val root = File(project.directory, "Plugins").apply { mkdirs() }
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            val manifest = File(dir, "plugin.json")
            if (!manifest.isFile) return@forEach
            val json = runCatching { JSONObject(manifest.readText()) }.getOrNull() ?: return@forEach
            val menus = mutableListOf<PluginMenu>()
            val arr = json.optJSONArray("menus") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                menus += PluginMenu(m.optString("id"), m.optString("label").ifBlank { m.optString("id") })
            }
            val hooks = mutableListOf<String>()
            val h = json.optJSONArray("hooks") ?: JSONArray()
            for (i in 0 until h.length()) hooks += h.optString(i)
            loaded += PluginRecord(
                id = json.optString("id", dir.name),
                title = json.optString("title", dir.name),
                enabled = json.optBoolean("enabled", true),
                entry = json.optString("entry", "main.js"),
                hooks = hooks,
                menus = menus,
                directory = dir,
                summary = json.optString("summary").ifBlank { "JS plugin · ${hooks.joinToString()}" },
            )
        }
        return loaded.toList()
    }

    fun create(project: ProjectStore.Project, rawId: String, title: String, code: String = ""): PluginRecord {
        val id = ProjectStore.sanitizeName(rawId).ifBlank { "plugin" }.lowercase()
        val dir = File(project.directory, "Plugins/$id").apply { mkdirs() }
        val manifest = JSONObject()
            .put("id", id)
            .put("title", title.ifBlank { id })
            .put("enabled", true)
            .put("entry", "main.js")
            .put("summary", "User plugin")
            .put("hooks", JSONArray().put("onInstall").put("onSave").put("onPlay").put("onAddObject"))
            .put("menus", JSONArray().put(JSONObject().put("id", "ping").put("label", "Ping")))
        File(dir, "plugin.json").writeText(manifest.toString(2) + "\n")
        val body = if (code.isNotBlank()) {
            runCatching { ScriptInterpreter(code) }.onFailure {
                File(dir, "main.js").writeText(fallbackJs(id))
                error("plugin JS: ${it.message}")
            }
            code.trim() + "\n"
        } else fallbackJs(id)
        File(dir, "main.js").writeText(body)
        return reload(project).first { it.id == id }
    }

    fun setEnabled(project: ProjectStore.Project, id: String, enabled: Boolean) {
        val rec = loaded.find { it.id == id } ?: return
        val file = File(rec.directory, "plugin.json")
        val json = JSONObject(file.readText())
        json.put("enabled", enabled)
        file.writeText(json.toString(2) + "\n")
        reload(project)
    }

    fun run(plugin: PluginRecord, fn: String, env: MutableMap<String, ScriptInterpreter.Val>): String {
        if (!plugin.enabled) return "disabled"
        val src = File(plugin.directory, plugin.entry)
        if (!src.isFile) return "no ${plugin.entry}"
        val script = runCatching { ScriptInterpreter(src.readText()) }.getOrElse { return "ERROR parse ${plugin.id}: ${it.message}" }
        if (!script.has(fn)) return "no function $fn"
        return runCatching { script.call(fn, env).str() }.getOrElse { "ERROR: ${it.message}" }
    }

    companion object {
        fun fallbackJs(id: String): String = """
            function onInstall(api) {
              api.log("$id installed");
              api.writeFile("Logs/$id.txt", "installed " + api.time);
            }
            function onSave(api) { api.log("$id onSave " + api.path); }
            function onPlay(api) { api.log("$id onPlay"); }
            function onAddObject(api) { api.log("$id add " + api.type + " " + api.name); }
            function menu_ping(api) {
              api.writeFile("Logs/$id-ping.txt", "ping " + api.time);
              api.notify("$id ping → Logs/$id-ping.txt");
            }
        """.trimIndent() + "\n"
    }
}
