package com.mobileforge

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileforge.agent.AgentCard
import com.mobileforge.agent.AgentEvent
import com.mobileforge.agent.AgentParser
import com.mobileforge.agent.Director
import com.mobileforge.engine.AssetKitchen
import com.mobileforge.ai.AiGateway
import com.mobileforge.ai.AiResult
import com.mobileforge.ai.ChatTurn
import com.mobileforge.ai.ModelCatalog
import com.mobileforge.ai.Provider
import com.mobileforge.ai.StreamText
import com.mobileforge.cloud.GhAccount
import com.mobileforge.cloud.GhRepo
import com.mobileforge.cloud.GhRun
import com.mobileforge.cloud.GitHubService
import com.mobileforge.engine.Completions
import com.mobileforge.engine.ControlLayout
import com.mobileforge.engine.CsTranspiler
import com.mobileforge.engine.GameRuntime
import com.mobileforge.engine.SoundBank
import com.mobileforge.engine.Orbit
import com.mobileforge.engine.Suggestion
import com.mobileforge.export.HtmlPreview
import com.mobileforge.mcp.McpHub
import com.mobileforge.mcp.McpServer
import com.mobileforge.mcp.McpTool
import com.mobileforge.engine.ChangeLog
import com.mobileforge.engine.FileChange
import com.mobileforge.engine.ScriptInterpreter
import com.mobileforge.plugins.PluginHost
import com.mobileforge.plugins.PluginRecord
import com.mobileforge.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Section { Agent, Projects, Files, Studio, Assets, Play, Cloud, Mcp, Ai, Settings, Changes, Plugins }
enum class AiCommand { Create, Change, Delete, Explain }

data class ProjectItem(val name: String, val type: String)
data class FileItem(val path: String, val name: String)
data class ProposalFile(val path: String, val content: String, val previous: String)
data class PackItem(val path: String, val kind: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    private val scenesStore = SceneStore()
    private val secrets = SecretStore(app)
    private val ai = AiGateway(secrets)
    private val prefs: SharedPreferences =
        app.getSharedPreferences("mobileforge.providers", android.content.Context.MODE_PRIVATE)
    val github = GitHubService(secrets, prefs)
    private val mcp = McpHub(secrets, prefs, store, scenesStore, github)

    var section by mutableStateOf(Section.Agent)
    var toast by mutableStateOf<String?>(null)
    var projectName by mutableStateOf<String?>(null)
    var projects = mutableStateListOf<ProjectItem>()
    var files = mutableStateListOf<FileItem>()
    var openPath by mutableStateOf<String?>(null)
    var editorText by mutableStateOf("")
    var dirty by mutableStateOf(false)
    var cursor by mutableStateOf(0)
    var suggestions = mutableStateListOf<Suggestion>()
    var scenes = mutableStateListOf<GameScene>()
    var scene by mutableStateOf<GameScene?>(null)
    var selected by mutableStateOf<String?>(null)
    val orbit = Orbit()
    var runtime by mutableStateOf<GameRuntime?>(null)
    var playHud by mutableStateOf("")
    var playControls by mutableStateOf(ControlLayout.EMPTY)
    var provider by mutableStateOf("zen")
    var model by mutableStateOf("laguna-s-2.1-free")
    var customEndpoint by mutableStateOf("")
    var customModel by mutableStateOf("")
    var aiEvent by mutableStateOf("ON_UPDATE")
    var aiLanguage by mutableStateOf("C#")
    var aiCommand by mutableStateOf(AiCommand.Create)
    var aiTask by mutableStateOf("Создай C#-компонент движения игрока и подбора монет. Только код, без продуктовых решений.")
    var aiOut by mutableStateOf("Вы режиссёр. Нейросеть пишет только код. Apply — только после вашей команды.")
    var aiBusy by mutableStateOf(false)
    var proposal = mutableStateListOf<ProposalFile>()
    var zenKey by mutableStateOf("")
    var orKey by mutableStateOf("")
    var mcpKey by mutableStateOf("")
    var customKey by mutableStateOf("")
    var orcaKey by mutableStateOf("")
    var geminiKey1 by mutableStateOf("")
    var geminiKey2 by mutableStateOf("")
    var geminiKey3 by mutableStateOf("")
    var hasZen by mutableStateOf(false)
    var hasOr by mutableStateOf(false)
    var hasMcp by mutableStateOf(false)
    var hasCustom by mutableStateOf(false)
    var hasOrca by mutableStateOf(false)
    var hasGemini by mutableStateOf(false)
    var dialog by mutableStateOf<String?>(null)
    var dialogValue by mutableStateOf("")
    var importText by mutableStateOf("")
    val console = mutableStateListOf<String>()
    var pack = mutableStateListOf<PackItem>()
    var ghAccounts = mutableStateListOf<GhAccount>()
    var ghRepos = mutableStateListOf<GhRepo>()
    var ghRuns = mutableStateListOf<GhRun>()
    var ghLabel by mutableStateOf("work")
    var ghTokenInput by mutableStateOf("")
    var newRepoName by mutableStateOf("")
    var newRepoPrivate by mutableStateOf(true)
    var boundRepo by mutableStateOf("")
    var cloudBusy by mutableStateOf(false)
    var mcpServers = mutableStateListOf<McpServer>()
    var mcpTools = mutableStateListOf<McpTool>()
    var mcpTool by mutableStateOf("fs.list")
    var mcpArgs by mutableStateOf("{}")
    var mcpOut by mutableStateOf("Локальные MCP-инструменты готовы. Удалённые — HTTPS или 127.0.0.1.")
    var mcpServerId by mutableStateOf("local")
    var mcpNewName by mutableStateOf("")
    var mcpNewUrl by mutableStateOf("https://")
    var mcpNewToken by mutableStateOf("")
    var agentInput by mutableStateOf("")
    var agentRunning by mutableStateOf(false)
    var agentStatus by mutableStateOf("готов")
    var agentRound by mutableStateOf(0)
    var agentToolsUsed by mutableStateOf(0)
    var agentTokens by mutableStateOf(0)
    var agentElapsed by mutableStateOf(0)
    var agentMenu by mutableStateOf(false)
    val agentCards = mutableStateListOf<AgentCard>()
    val agentFeed = mutableStateListOf<AgentEvent>()
    var streamTick by mutableStateOf(0)
    private var agentCancel = false
    private var lastDirectorTask = ""
    private var lastSubstantialTask = ""
    private var lastUtterance = ""
    var agentLastLlmMs by mutableStateOf(0L)
    var agentLastToolMs by mutableStateOf(0L)
    private val pluginHost = PluginHost()
    val pluginRecords = mutableStateListOf<PluginRecord>()
    val pluginLog = mutableStateListOf<String>()
    var pluginNewId by mutableStateOf("")
    var pluginNewTitle by mutableStateOf("")
    val changes = mutableStateListOf<FileChange>()
    var selectedChange by mutableStateOf(0L)
    val ideTabs = mutableStateListOf<String>()
    var findQuery by mutableStateOf("")
    val findHits = mutableStateListOf<String>()
    val ideErrors = mutableStateListOf<String>()
    private val sessionStarted = System.currentTimeMillis()
    val sessionLimitMs = 6L * 60 * 60 * 1000
    fun sessionElapsedMs(): Long = System.currentTimeMillis() - sessionStarted
    fun sessionLeftMs(): Long = (sessionLimitMs - sessionElapsedMs()).coerceAtLeast(0)

    val models: List<String>
        get() = (ModelCatalog.idsFor(provider) + listOfNotNull(customModel.ifBlank { null })).distinct()

    fun projectsRoot(): String = store.root.absolutePath

    fun projectPath(): String =
        projectName?.let { store.find(it)?.directory?.absolutePath } ?: store.root.absolutePath

    fun copyProjectPath() {
        val path = projectPath()
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("project", path))
        notify("Путь: $path")
    }

    fun setRoute(nextProvider: String, nextModel: String) {
        provider = nextProvider
        model = nextModel
        prefs.edit()
            .putString("ai_provider", nextProvider)
            .putString("${nextProvider}_model", nextModel)
            .apply()
    }

    private var soundBank: SoundBank? = null

    init {
        refreshProjects()
        refreshProviderFlags()
        refreshGithub()
        refreshMcp()
        refreshPack()
        boundRepo = prefs.getString("gh_bound_repo", "").orEmpty()
        customEndpoint = prefs.getString("custom_endpoint", "").orEmpty()
        provider = prefs.getString("ai_provider", "zen").orEmpty().ifBlank { "zen" }
        model = prefs.getString("${provider}_model", ModelCatalog.defaultId(provider))
            .orEmpty().ifBlank { ModelCatalog.defaultId(provider) }
        lastSubstantialTask = prefs.getString("last_order", "").orEmpty()
        soundBank = SoundBank(getApplication())
        restoreFeed()
        val lastProj = prefs.getString("last_project", "").orEmpty()
        if (lastProj.isNotBlank() && store.find(lastProj) != null) {
            openProject(lastProj, navigate = false)
        }
        installCrashGuard()
        log("MobileForge 2.9 — чат/проект на диске, Play без вылета, ассеты, плагины")
    }


    private fun feedFile(): File = File(getApplication<Application>().filesDir, "agent_feed.json")

    private fun persistFeed() {
        runCatching {
            val arr = JSONArray()
            agentFeed.takeLast(150).forEach { ev ->
                val o = JSONObject()
                when (ev) {
                    is AgentEvent.User -> o.put("k", "u").put("id", ev.id).put("t", ev.text)
                    is AgentEvent.Assistant -> o.put("k", "a").put("id", ev.id)
                        .put("t", ev.text.take(4000)).put("th", ev.thinking.take(12000)).put("ms", ev.ms)
                    is AgentEvent.Tool -> o.put("k", "o").put("id", ev.id).put("title", ev.title)
                        .put("tool", ev.tool).put("args", ev.args.take(4000)).put("result", ev.result.take(4000))
                        .put("ms", ev.ms).put("ok", ev.ok)
                }
                arr.put(o)
            }
            feedFile().writeText(arr.toString())
        }
    }

    private fun restoreFeed() {
        val f = feedFile()
        if (!f.isFile) return
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", System.nanoTime() + i)
                when (o.optString("k")) {
                    "u" -> agentFeed += AgentEvent.User(id, o.optString("t"))
                    "a" -> agentFeed += AgentEvent.Assistant(
                        id = id,
                        text = o.optString("t"),
                        thinking = o.optString("th"),
                        live = false,
                        ms = o.optLong("ms"),
                    )
                    "o" -> agentFeed += AgentEvent.Tool(
                        id = id,
                        title = o.optString("title"),
                        tool = o.optString("tool"),
                        args = o.optString("args"),
                        result = o.optString("result"),
                        ms = o.optLong("ms"),
                        ok = o.optBoolean("ok", true),
                        expanded = false,
                    )
                }
            }
        }
    }

    private fun installCrashGuard() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching {
                persistFeed()
                File(getApplication<Application>().filesDir, "crash.txt")
                    .writeText("${err.javaClass.name}: ${err.message}\n${err.stackTraceToString().take(4000)}")
            }
            prev?.uncaughtException(thread, err)
        }
    }

    fun notify(text: String) {
        toast = text
    }

    fun log(text: String) {
        console.add(0, text)
        while (console.size > 200) console.removeAt(console.lastIndex)
    }

    fun go(next: Section) {
        if (next != Section.Play) runtime?.stop()
        section = next
        when (next) {
            Section.Projects -> refreshProjects()
            Section.Files, Section.Studio, Section.Assets, Section.Ai, Section.Changes, Section.Plugins -> {
                refreshFiles()
                refreshScenes()
                refreshPack()
                if (next == Section.Plugins) reloadPlugins()
                if (next == Section.Changes) reloadChanges()
                if (next == Section.Ai) scanIdeErrors()
            }
            Section.Play -> {}
            Section.Settings -> refreshProviderFlags()
            Section.Cloud -> refreshGithub()
            Section.Mcp -> refreshMcp()
            Section.Agent -> {}
        }
    }

    fun back(): Boolean {
        if (agentRunning) {
            stopAgent()
            return true
        }
        if (runtime?.playing == true) {
            stopPlay()
            return true
        }
        return when (section) {
            Section.Agent -> false
            else -> { go(Section.Agent); true }
        }
    }

    fun refreshProjects() {
        projects.clear()
        store.projects().forEach { p ->
            val type = store.meta(p).optString("type", "3d")
            projects += ProjectItem(p.name, type)
        }
    }

    fun openProject(name: String, navigate: Boolean = true) {
        projectName = name
        dirty = false
        openPath = null
        editorText = ""
        refreshFiles()
        refreshScenes()
        scene = scenes.firstOrNull()
        selected = scene?.objects?.firstOrNull()?.name
        ideTabs.clear()
        reloadPlugins()
        reloadChanges()
        firePlugin("onOpen")
        prefs.edit().putString("last_project", name).apply()
        log("Проект открыт: $name")
        notify("Проект: $name")
        if (navigate) go(Section.Studio)
    }

    fun createProject(raw: String, type: String, navigate: Boolean = true) {
        store.create(raw, type).fold(
            { openProject(it.name, navigate) },
            { notify(it.message ?: "Ошибка создания") },
        )
    }

    fun deleteProject(name: String) {
        store.delete(name).fold(
            {
                if (projectName == name) {
                    projectName = null
                    scene = null
                    openPath = null
                }
                refreshProjects()
            },
            { notify(it.message ?: "Ошибка") },
        )
    }

    fun seedDemo() {
        runCatching { store.seedDemo() }.fold(
            { openProject(it.name) },
            { notify(it.message ?: "Демо не создано") },
        )
    }

    fun importProject(json: String) {
        runCatching { store.importBundle(JSONObject(json)).getOrThrow() }.fold(
            { openProject(it.name); dialog = null },
            { notify(it.message ?: "Импорт не удался") },
        )
    }

    fun exportProject(name: String): String? {
        val project = store.find(name) ?: return null.also { notify("Нет проекта") }
        return store.exportBundle(project).toString(2)
    }

    fun refreshFiles() {
        val project = current(false) ?: return
        files.clear()
        store.files(project).forEach { files += FileItem(it.relativePath, it.file.name) }
    }

    fun refreshPack() {
        pack.clear()
        runCatching {
            val am = getApplication<Application>().assets
            fun walk(prefix: String) {
                val kids = am.list(prefix) ?: return
                if (kids.isEmpty() && prefix.contains('.')) {
                    pack += PackItem(prefix, prefix.substringAfterLast('.'))
                    return
                }
                kids.forEach { name ->
                    val path = if (prefix.isBlank()) name else "$prefix/$name"
                    val nested = am.list(path)
                    if (nested.isNullOrEmpty()) pack += PackItem(path, name.substringAfterLast('.'))
                    else walk(path)
                }
            }
            walk("StudioPack")
        }
    }

    fun openFile(path: String) {
        val project = current() ?: return
        val file = runCatching { store.resolve(project, path) }.getOrNull() ?: return notify("Файл не найден")
        if (!file.isFile) return notify("Файл не найден")
        openPath = path
        editorText = file.readText()
        dirty = false
        suggestions.clear()
        if (path !in ideTabs) ideTabs += path
        scanIdeErrors()
        if (path.endsWith(".scene.json")) {
            runCatching {
                scene = scenesStore.load(file)
                selected = scene?.objects?.firstOrNull()?.name
            }
        }
        refreshFiles()
    }

    fun onEditorChange(text: String, cursorPos: Int) {
        editorText = text
        cursor = cursorPos
        dirty = true
        val prefix = Completions.prefixAt(text, cursorPos).second
        val extras = files.map { it.name.substringBeforeLast('.') } +
            (scene?.objects?.map { it.name } ?: emptyList())
        suggestions.clear()
        if (prefix.length >= 1) suggestions += Completions.suggest(prefix, openPath, extras)
    }

    fun applySuggestion(item: Suggestion) {
        val (start, prefix) = Completions.prefixAt(editorText, cursor)
        editorText = editorText.substring(0, start) + item.insert + editorText.substring(start + prefix.length)
        cursor = start + item.insert.length
        dirty = true
        suggestions.clear()
    }

    fun saveFile() {
        val project = current() ?: return notify("Нет проекта")
        val path = openPath ?: return notify("Нет открытого файла")
        val before = runCatching { store.resolve(project, path).takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
        store.writeFile(project, path, editorText).fold(
            {
                dirty = false
                if (path.endsWith(".scene.json")) {
                    runCatching { scene = scenesStore.load(it.file) }
                }
                recordWrite(path, "вы", before, editorText)
                firePlugin("onSave", extraPath = path)
                scanIdeErrors()
                notify("Сохранено: $path")
                refreshFiles()
            },
            { notify(it.message ?: "Ошибка записи") },
        )
    }

    fun createFile(path: String) {
        val project = current() ?: return
        val seed = when {
            path.endsWith(".cs") -> "using MobileForge;\npublic class Component : ForgeBehaviour {\n    public float speed = 6f;\n    void Start() { }\n    void Update() {\n        // director writes intent, AI writes code\n    }\n}\n"
            path.endsWith(".js") -> "function onUpdate(api, dt) {\n  \n}\n"
            path.endsWith(".json") -> "{\n  \n}\n"
            path.endsWith(".mat") -> "{\n  \"shader\": \"standard\",\n  \"color\": \"#b69cff\",\n  \"metallic\": 0.0,\n  \"smoothness\": 0.5\n}\n"
            else -> "// $path\n"
        }
        store.createFile(project, path, seed).fold(
            {
                recordWrite(path, "вы", "", seed)
                openFile(path)
            },
            { notify(it.message ?: "Не создан") },
        )
    }

    fun deleteOpenFile() {
        val project = current() ?: return
        val path = openPath ?: return
        val beforeDel = runCatching { store.resolve(project, path).readText() }.getOrNull().orEmpty()
        store.deleteFile(project, path).fold(
            {
                recordWrite(path, "вы", beforeDel, "")
                ideTabs.remove(path)
                openPath = null
                editorText = ""
                dirty = false
                refreshFiles()
            },
            { notify(it.message ?: "Не удалён") },
        )
    }

    fun refreshScenes() {
        val project = current(false) ?: return
        scenes.clear()
        scenes += scenesStore.scenes(project)
        if (scene == null) scene = scenes.firstOrNull()
    }

    fun createScene(name: String, dim: String) {
        val project = current() ?: return
        scenesStore.create(project, name, dim).fold(
            {
                scene = it
                selected = it.objects.firstOrNull { obj -> obj.type != "Camera" }?.name
                    ?: it.objects.firstOrNull()?.name
                refreshScenes()
                notify("Сцена ${it.name}")
            },
            { err ->
                val msg = err.message.orEmpty()
                if ("already exists" in msg) {
                    refreshScenes()
                    scene = scenes.firstOrNull { it.name.equals(name, true) } ?: scene
                    notify("Сцена $name уже есть — открыл")
                } else {
                    notify(msg.ifBlank { "Ошибка сцены" })
                }
            },
        )
    }

    fun deleteScene(name: String) {
        val project = current() ?: return
        scenesStore.delete(project, name).fold(
            {
                if (scene?.name == name) scene = null
                refreshScenes()
            },
            { notify(it.message ?: "Ошибка") },
        )
    }

    fun persistScene(show: Boolean = false) {
        current(false) ?: return
        val currentScene = scene ?: return
        val scenePath = "Scenes/${currentScene.name}.scene.json"
        val sceneBefore = runCatching { currentScene.file.takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
        scenesStore.save(currentScene)
        recordWrite(scenePath, "сцена", sceneBefore, currentScene.toJson().toString(2) + "\n")
        if (openPath == "Scenes/${currentScene.name}.scene.json") {
            editorText = currentScene.toJson().toString(2) + "\n"
            dirty = false
        }
        if (show) notify("Сцена сохранена")
        refreshScenes()
    }

    fun selectObject(name: String) {
        selected = name
    }

    fun updateSelected(mutate: (SceneObject) -> Unit) {
        val obj = scene?.objects?.firstOrNull { it.name == selected } ?: return
        val old = obj.name
        mutate(obj)
        if (obj.name != old) selected = obj.name
        persistScene(false)
    }

    fun addObject(type: String) {
        addObjectFromArgs(org.json.JSONObject().put("type", type))
    }

    fun addObjectFromArgs(args: org.json.JSONObject): String {
        val currentScene = scene ?: return "ERROR: no scene"
        val type = args.optString("type", "Mesh").ifBlank { "Mesh" }
        val wanted = args.optString("name").ifBlank { type }
        if (args.optString("name").isBlank() && currentScene.objects.any { it.type == type && type in listOf("Player", "Ground") }) {
            return "SKIPPED: $type already exists. Pass a unique name if you really need another."
        }
        var name = ProjectStore.sanitizeName(wanted).ifBlank { type }
        if (currentScene.objects.any { it.name == name }) {
            var i = 2
            while (currentScene.objects.any { it.name == "${name}_$i" }) i++
            name = "${name}_$i"
        }
        val color = args.optString("color").ifBlank {
            when (type) {
                "Coin" -> "#f4c95d"
                "Enemy" -> "#ffb2c8"
                "Light" -> "#fff4cc"
                "Block" -> "#6ea8fe"
                "Ground", "Plane" -> "#2a3144"
                else -> "#b69cff"
            }
        }
        val mesh = args.optString("mesh").ifBlank { SceneObject.defaultMesh(type) }
        var material = args.optString("material")
        if (material == "null") material = ""
        if (material.isBlank()) {
            current(false)?.let { p ->
                val guess = listOf(
                    "Assets/Materials/$name.mat",
                    "Assets/Materials/${name}Mat.mat",
                    "Materials/$name.mat",
                )
                material = guess.firstOrNull { store.resolve(p, it).isFile }.orEmpty()
            }
        }
        val extra = org.json.JSONObject()
        args.optString("pattern").takeIf { it.isNotBlank() && it != "null" }?.let { extra.put("pattern", it) }
        args.optString("accent").takeIf { it.isNotBlank() && it != "null" }?.let { extra.put("accent", it) }
        if (material.isNotBlank()) {
            current(false)?.let { p ->
                runCatching {
                    val text = store.resolve(p, material).takeIf { it.isFile }?.readText().orEmpty()
                    val (c, pat, acc) = AssetKitchen.parseMat(text)
                    extra.put("pattern", pat)
                    extra.put("accent", acc)
                    extra.put("material", material)
                    if (args.optString("color").isBlank()) extra.put("baked", c)
                }
            }
        }
        val baked = extra.optString("baked")
        val onGround = type == "Ground" || type == "Plane"
        currentScene.objects += SceneObject(
            name = name,
            type = type,
            x = args.optDouble("x", if (onGround) 0.0 else 0.0).toFloat(),
            y = args.optDouble("y", if (type == "Camera") 5.0 else if (onGround) 0.0 else 1.0).toFloat(),
            z = args.optDouble("z", if (type == "Camera") 10.0 else if (type == "Player") 4.0 else 0.0).toFloat(),
            sx = args.optDouble("sx", if (onGround) 16.0 else 1.0).toFloat(),
            sy = args.optDouble("sy", if (onGround) 1.0 else 1.0).toFloat(),
            sz = args.optDouble("sz", if (onGround) 16.0 else 1.0).toFloat(),
            color = baked.ifBlank { color },
            solid = type != "Camera" && type != "Light" && type != "Coin",
            mesh = mesh,
            material = material,
            script = args.optString("script").ifBlank { if (type == "Player") "Scripts/Player.js" else "" },
            extra = extra,
            lightType = if (type == "Light") "Directional" else "Directional",
        )
        selected = name
        persistScene(true)
        firePlugin("onAddObject", extraPath = name, extraType = type)
        log("Добавлен $type $name")
        return "added $name"
    }

    fun deleteSelected() {
        val currentScene = scene ?: return
        currentScene.objects.removeAll { it.name == selected }
        selected = currentScene.objects.firstOrNull()?.name
        persistScene(true)
    }

    fun startPlay() {
        runCatching { startPlayUnsafe() }.onFailure { err ->
            runCatching { runtime?.stop() }
            runtime = null
            val msg = err.message ?: err.javaClass.simpleName
            log("Play crash: $msg")
            notify("Play не запустился: $msg")
        }
    }

    private fun startPlayUnsafe() {
        val project = current() ?: return
        if (scene == null) refreshScenes()
        val currentScene = scene ?: scenes.firstOrNull() ?: return notify("Нет сцены")
        val scripts = HashMap<String, String>()
        currentScene.objects.forEach { obj ->
            if (obj.script.isNotBlank()) {
                runCatching { scripts[obj.script] = store.resolve(project, obj.script).readText() }
            }
        }
        playControls = runCatching {
            val file = store.resolve(project, "UI/Controls.json")
            if (file.isFile) ControlLayout.parse(file.readText()) else ControlLayout.EMPTY
        }.getOrDefault(ControlLayout.EMPTY)
        runtime?.stop()
        val bank = soundBank ?: SoundBank(getApplication()).also { soundBank = it }
        val wavs = runCatching { bank.loadFrom(project.directory) }.getOrDefault(emptyList())
        runtime = GameRuntime(
            currentScene,
            scripts,
            onSound = { name -> runCatching { bank.play(name) } },
        ).also { it.start() }
        runCatching { firePlugin("onPlay") }
        log(
            buildString {
                append(
                    if (playControls.items.isEmpty()) {
                        "Play: сенсор не задан"
                    } else {
                        "Play: ${playControls.items.size} виджет(ов)"
                    },
                )
                if (wavs.isEmpty()) append(" · wav нет (Assets/Audio)")
                else append(" · звуки ${wavs.joinToString()}")
            },
        )
    }

    fun stopPlay() {
        runtime?.stop()
        runtime = null
        playHud = ""
        log("Play stopped")
    }

    fun tickPlay(dt: Float) {
        val rt = runtime ?: return
        rt.step(dt)
        rt.input.jump = false
        playHud = "${projectName ?: ""}  •  score ${rt.score}" +
            (rt.log.firstOrNull()?.let { "  •  $it" } ?: "")
    }

    fun generateAi() {
        if (aiTask.isBlank()) return notify("Сформулируйте приказ")
        aiBusy = true
        aiOut = "Пишу только код по вашей команде…"
        val prompt = buildPrompt()
        val endpoint = customEndpoint
        val chosen = customModel.ifBlank { model }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ai.send(Provider.fromId(provider), chosen, prompt, endpoint) }
                    .getOrElse { AiResult.Failure(it.message ?: "AI error") }
            }
            aiBusy = false
            when (result) {
                is AiResult.Success -> {
                    aiOut = result.text
                    val parsed = CsTranspiler.parseProposal(result.text).map { (path, content) ->
                        val resolved = path.ifBlank { CsTranspiler.guessPath(aiLanguage, aiEvent, openPath) }
                        val previous = current(false)?.let { p ->
                            runCatching { store.resolve(p, resolved).takeIf { it.isFile }?.readText() }.getOrNull()
                        }.orEmpty()
                        ProposalFile(resolved, content, previous)
                    }
                    proposal.clear()
                    proposal += parsed
                    log("AI proposal: ${parsed.size} файл(ов). Apply только вами.")
                    notify("Код готов — Review / Apply")
                }
                is AiResult.Failure -> {
                    aiOut = "Ошибка: ${result.message}"
                    notify(result.message)
                }
            }
        }
    }

    fun applyProposal() {
        val project = current() ?: return notify("Нет проекта")
        if (proposal.isEmpty()) return notify("Сначала приказ → Generate")
        proposal.forEach { file ->
            store.writeFile(project, file.path, file.content).onFailure {
                notify("${file.path}: ${it.message}")
            }
        }
        notify("Применено вами: ${proposal.size}")
        log("Пользователь применил ${proposal.size} файл(ов)")
        refreshFiles()
        refreshScenes()
        openPath?.let { path ->
            proposal.find { it.path == path }?.let { editorText = it.content; dirty = false }
        }
    }

    fun saveSettings() {
        if (zenKey.isNotBlank()) secrets.put("zen_key", zenKey)
        if (orKey.isNotBlank()) secrets.put("openrouter_key", orKey)
        if (mcpKey.isNotBlank()) secrets.put("mcp_token", mcpKey)
        if (customKey.isNotBlank()) secrets.put("custom_key", customKey)
        if (orcaKey.isNotBlank()) secrets.put("orca_key", orcaKey)
        if (geminiKey1.isNotBlank()) secrets.put("gemini_key_1", geminiKey1)
        if (geminiKey2.isNotBlank()) secrets.put("gemini_key_2", geminiKey2)
        if (geminiKey3.isNotBlank()) secrets.put("gemini_key_3", geminiKey3)
        if (customEndpoint.isNotBlank()) {
            if (!customEndpoint.startsWith("https://")) notify("Custom endpoint только HTTPS")
            else secrets.put("custom_endpoint", customEndpoint)
        }
        prefs.edit()
            .putString("custom_endpoint", customEndpoint)
            .putString("${provider}_model", customModel.ifBlank { model })
            .apply()
        zenKey = ""; orKey = ""; mcpKey = ""; customKey = ""
        orcaKey = ""; geminiKey1 = ""; geminiKey2 = ""; geminiKey3 = ""
        refreshProviderFlags()
        notify("Ключи в Keystore")
    }

    fun checkMcp() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ai.pingMcp() }
            notify(
                when (result) {
                    is AiResult.Success -> "MCP: ${result.text.take(80)}"
                    is AiResult.Failure -> "MCP: ${result.message}"
                },
            )
        }
    }

    fun htmlPreview(): String? {
        val currentScene = scene ?: return null.also { notify("Нет сцены") }
        return HtmlPreview.render(currentScene)
    }

    fun selectedObject(): SceneObject? = scene?.objects?.firstOrNull { it.name == selected }

    fun refreshGithub() {
        ghAccounts.clear()
        ghAccounts += github.accounts()
    }

    fun addGithubAccount() {
        viewModelScope.launch {
            cloudBusy = true
            val result = withContext(Dispatchers.IO) { github.addAccount(ghLabel, ghTokenInput) }
            cloudBusy = false
            result.fold(
                {
                    ghTokenInput = ""
                    refreshGithub()
                    log("GitHub аккаунт ${it.login} добавлен")
                    notify("Аккаунт ${it.login}")
                },
                { notify(it.message ?: "PAT отклонён") },
            )
        }
    }

    fun selectGithubAccount(id: String) {
        github.activeId = id
        refreshGithub()
        notify("Активен ${ghAccounts.find { it.id == id }?.login ?: id}")
    }

    fun removeGithubAccount(id: String) {
        github.removeAccount(id)
        refreshGithub()
    }

    fun loadRepos() {
        viewModelScope.launch {
            cloudBusy = true
            val result = withContext(Dispatchers.IO) { github.listRepos() }
            cloudBusy = false
            result.fold(
                {
                    ghRepos.clear()
                    ghRepos += it
                    log("Репозиториев: ${it.size}")
                },
                { notify(it.message ?: "repos failed") },
            )
        }
    }

    fun bindRepo(name: String) {
        boundRepo = name
        prefs.edit().putString("gh_bound_repo", name).apply()
        notify("Привязан $name")
    }

    fun createRepo() {
        viewModelScope.launch {
            cloudBusy = true
            val result = withContext(Dispatchers.IO) {
                github.createRepo(newRepoName, newRepoPrivate, "MobileForge project")
            }
            cloudBusy = false
            result.fold(
                {
                    bindRepo(it.fullName)
                    loadRepos()
                    notify("Создан ${it.fullName}")
                },
                { notify(it.message ?: "create repo failed") },
            )
        }
    }

    fun pushProjectToGithub() {
        val project = current() ?: return
        val repo = boundRepo.ifBlank { return notify("Сначала выберите репозиторий") }
        viewModelScope.launch {
            cloudBusy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    store.files(project).forEach { file ->
                        github.putFile(
                            repo,
                            "project/${file.relativePath}",
                            file.file.readText(),
                            "studio: ${file.relativePath}",
                        ).getOrThrow()
                    }
                    github.putFile(
                        repo,
                        "project/README.md",
                        "# ${project.name}\n\nSynced from MobileForge phone editor. Builds run on GitHub Actions.\n",
                        "studio: readme",
                    ).getOrThrow()
                }
            }
            cloudBusy = false
            result.fold(
                { notify("Проект залит в $repo/project"); log("Push ${project.name} → $repo") },
                { notify(it.message ?: "push failed") },
            )
        }
    }

    fun triggerCloudBuild() {
        val repo = boundRepo.ifBlank { return notify("Нет репозитория") }
        viewModelScope.launch {
            cloudBusy = true
            val result = withContext(Dispatchers.IO) { github.triggerWorkflow(repo, "android.yml") }
            cloudBusy = false
            result.fold(
                { notify("Runner запущен"); log("workflow_dispatch $repo") },
                { notify(it.message ?: "dispatch failed") },
            )
        }
    }

    fun loadRuns() {
        val repo = boundRepo.ifBlank { return notify("Нет репозитория") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { github.runs(repo) }
            result.fold(
                { ghRuns.clear(); ghRuns += it },
                { notify(it.message ?: "runs failed") },
            )
        }
    }

    fun refreshMcp() {
        mcpServers.clear()
        mcpServers += mcp.servers()
        mcpTools.clear()
        mcpTools += mcp.builtInTools()
    }

    fun addMcpServer() {
        mcp.addServer(mcpNewName, mcpNewUrl, mcpNewToken).fold(
            {
                mcpNewToken = ""
                refreshMcp()
                notify("MCP ${it.name}")
            },
            { notify(it.message ?: "MCP не добавлен") },
        )
    }

    fun runMcpTool() {
        viewModelScope.launch {
            val args = runCatching { JSONObject(mcpArgs.ifBlank { "{}" }) }.getOrElse {
                notify("args не JSON"); return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (mcpServerId == "local") {
                        mcp.callLocal(
                            mcpTool, args, projectName, scene,
                            onLog = { log(it) },
                            addObject = { addObject(it) },
                            boundRepo = boundRepo,
                        )
                    } else {
                        val server = mcp.servers().first { it.id == mcpServerId }
                        mcp.callRemote(server, mcpTool, args)
                    }
                }
            }
            result.fold(
                { mcpOut = it; log("MCP $mcpTool ok") },
                { mcpOut = it.message ?: "fail"; notify(mcpOut) },
            )
        }
    }

    fun plugins() = pluginRecords.map {
        com.mobileforge.plugins.PluginInfo(it.id, it.title, it.summary, it.enabled)
    }

    fun reloadPlugins() {
        pluginRecords.clear()
        val p = current(false) ?: return
        pluginRecords += pluginHost.reload(p)
    }

    fun createPlugin() = createPluginFromArgs(pluginNewId, pluginNewTitle)

    fun createPluginFromArgs(id: String, title: String, code: String = ""): String {
        val p = current(false) ?: return "ERROR: no project"
        val rec = pluginHost.create(p, id.ifBlank { "plugin" }, title.ifBlank { id }, code)
        reloadPlugins()
        refreshFiles()
        firePlugin("onInstall")
        notify("Плагин ${rec.id}")
        return "created plugin ${rec.id}"
    }

    fun togglePlugin(id: String) {
        val p = current(false) ?: return
        val rec = pluginRecords.find { it.id == id } ?: return
        pluginHost.setEnabled(p, id, !rec.enabled)
        reloadPlugins()
    }

    fun runPluginMenu(id: String, menu: String): String {
        val rec = pluginRecords.find { it.id == id } ?: return "no plugin"
        val fn = if (menu.startsWith("menu_")) menu else "menu_$menu"
        val out = pluginHost.run(rec, fn, pluginEnv())
        pluginLog.add(0, "$id.$fn → $out")
        while (pluginLog.size > 40) pluginLog.removeAt(pluginLog.lastIndex)
        refreshFiles()
        return out
    }

    fun firePlugin(hook: String, extraPath: String = "", extraType: String = "") {
        val p = current(false) ?: return
        pluginRecords.filter { it.enabled && hook in it.hooks }.forEach { rec ->
            val out = runCatching { pluginHost.run(rec, hook, pluginEnv(extraPath, extraType)) }
                .getOrElse { "ERROR: ${it.message}" }
            if (out.isNotBlank() && out != "null") pluginLog.add(0, "${rec.id}.$hook → $out")
        }
        while (pluginLog.size > 40) pluginLog.removeAt(pluginLog.lastIndex)
    }

    private fun pluginEnv(extraPath: String = "", extraType: String = ""): MutableMap<String, ScriptInterpreter.Val> {
        val api = ScriptInterpreter.Val.Obj(
            get = { key ->
                when (key) {
                    "log" -> ScriptInterpreter.Val.Host { args ->
                        log(args.getOrNull(0)?.str().orEmpty()); ScriptInterpreter.Val.Null
                    }
                    "notify" -> ScriptInterpreter.Val.Host { args ->
                        notify(args.getOrNull(0)?.str().orEmpty()); ScriptInterpreter.Val.Null
                    }
                    "writeFile" -> ScriptInterpreter.Val.Host { args ->
                        val path = args.getOrNull(0)?.str().orEmpty()
                        val content = args.getOrNull(1)?.str().orEmpty()
                        val proj = current(false)
                        if (proj != null && path.isNotBlank()) {
                            val before = runCatching { store.resolve(proj, path).takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
                            store.writeFile(proj, path, content)
                            recordWrite(path, "плагин", before, content)
                        }
                        ScriptInterpreter.Val.Null
                    }
                    "readFile" -> ScriptInterpreter.Val.Host { args ->
                        val path = args.getOrNull(0)?.str().orEmpty()
                        val proj = current(false)
                        val text = if (proj != null) runCatching { store.resolve(proj, path).readText() }.getOrNull().orEmpty() else ""
                        ScriptInterpreter.Val.Str(text)
                    }
                    "addObject" -> ScriptInterpreter.Val.Host { args ->
                        addObject(args.getOrNull(0)?.str().orEmpty().ifBlank { "Mesh" })
                        ScriptInterpreter.Val.Null
                    }
                    "path" -> ScriptInterpreter.Val.Str(extraPath.ifBlank { openPath.orEmpty() })
                    "type" -> ScriptInterpreter.Val.Str(extraType)
                    "name" -> ScriptInterpreter.Val.Str(extraPath.ifBlank { selected.orEmpty() })
                    "project" -> ScriptInterpreter.Val.Str(projectName.orEmpty())
                    "time" -> ScriptInterpreter.Val.Num(System.currentTimeMillis().toDouble())
                    "selected" -> ScriptInterpreter.Val.Str(selected.orEmpty())
                    else -> ScriptInterpreter.Val.Null
                }
            },
            set = { _, _ -> },
        )
        return mutableMapOf("api" to api)
    }

    fun recordWrite(path: String, author: String, before: String, after: String) {
        if (before == after) return
        val ch = FileChange(System.nanoTime(), path, System.currentTimeMillis(), author, before, after)
        changes.add(0, ch)
        while (changes.size > 80) changes.removeAt(changes.lastIndex)
        val p = current(false) ?: return
        runCatching {
            val f = java.io.File(p.directory, "Logs/changes.jsonl")
            f.parentFile?.mkdirs()
            f.appendText(ChangeLog.toJson(ch).toString() + "\n")
        }
    }

    fun reloadChanges() {
        changes.clear()
        val p = current(false) ?: return
        val f = java.io.File(p.directory, "Logs/changes.jsonl")
        if (!f.isFile) return
        f.readLines().asReversed().take(80).forEach { line ->
            runCatching { changes += ChangeLog.fromJson(org.json.JSONObject(line)) }
        }
    }

    fun clearChanges() {
        changes.clear()
        selectedChange = 0L
        val p = current(false) ?: return
        java.io.File(p.directory, "Logs/changes.jsonl").delete()
    }

    fun findInProject() {
        findHits.clear()
        val q = findQuery.trim()
        if (q.isEmpty()) return
        val p = current(false) ?: return
        store.files(p).forEach { file ->
            runCatching {
                file.file.readLines().forEachIndexed { i, line ->
                    if (q in line) findHits += "${file.relativePath}:${i + 1}: ${line.trim().take(80)}"
                }
            }
        }
        if (findHits.isEmpty()) findHits += "нет совпадений"
    }

    fun scanIdeErrors() {
        ideErrors.clear()
        val text = editorText
        val path = openPath ?: return
        var curly = 0
        var round = 0
        var square = 0
        text.forEach { c ->
            when (c) {
                '{' -> curly++
                '}' -> curly--
                '(' -> round++
                ')' -> round--
                '[' -> square++
                ']' -> square--
            }
        }
        if (curly != 0) ideErrors += "$path: незакрытые { } ($curly)"
        if (round != 0) ideErrors += "$path: незакрытые ( ) ($round)"
        if (square != 0) ideErrors += "$path: незакрытые [ ] ($square)"
        if (path.endsWith(".json") || path.endsWith(".scene.json")) {
            runCatching { org.json.JSONObject(text) }.onFailure { ideErrors += "$path: JSON ${it.message}" }
        }
    }

    fun toggleCard(id: Long) {
        val i = agentCards.indexOfFirst { it.id == id }
        if (i >= 0) agentCards[i] = agentCards[i].copy(expanded = !agentCards[i].expanded)
        val f = agentFeed.indexOfFirst { it.id == id }
        if (f >= 0) {
            val ev = agentFeed[f]
            if (ev is AgentEvent.Tool) agentFeed[f] = ev.copy(expanded = !ev.expanded)
        }
    }

    fun stopAgent() {
        agentCancel = true
        agentRunning = false
        agentStatus = "стоп"
        log("Агент остановлен")
    }

    fun copyText(text: String) {
        if (text.isBlank()) return
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("mobileforge", text))
        notify("Скопировано")
    }

    fun resendToAgent(text: String) {
        val task = text.trim()
        if (task.isBlank() || agentRunning) return
        agentFeed += AgentEvent.User(System.nanoTime(), task)
        streamTick++
        persistFeed()
        runAgent(task)
    }

    fun submitAgent() {
        val task = agentInput.trim()
        if (task.isBlank() || agentRunning) return
        agentInput = ""
        agentFeed += AgentEvent.User(System.nanoTime(), task)
        streamTick++
        persistFeed()
        runAgent(task)
    }

    private fun sessionTranscript(limitChars: Int = 9000): String {
        val sb = StringBuilder()
        agentFeed.forEach { ev ->
            when (ev) {
                is AgentEvent.User -> sb.appendLine("РЕЖИССЁР: ${ev.text}")
                is AgentEvent.Assistant -> {
                    if (!ev.live && ev.text.isNotBlank()) sb.appendLine("АГЕНТ: ${ev.text.take(500)}")
                }
                is AgentEvent.Tool -> sb.appendLine("ИНСТРУМЕНТ ${ev.tool} (${Director.formatMs(ev.ms)}): ${ev.result.take(240)}")
            }
        }
        val s = sb.toString()
        return if (s.length <= limitChars) s else s.takeLast(limitChars)
    }

    fun runAgent(task: String) {
        val follow = Director.isFollowUp(task)
        if (!follow) {
            lastSubstantialTask = task
            prefs.edit().putString("last_order", task).apply()
        }
        val order = if (follow && lastSubstantialTask.isNotBlank()) lastSubstantialTask else task
        lastDirectorTask = order
        lastUtterance = task
        agentCancel = false
        agentRunning = true
        agentStatus = "работа"
        agentRound = 0
        agentToolsUsed = 0
        val started = System.currentTimeMillis()
        val named = if (follow && !projectName.isNullOrBlank()) {
            projectName
        } else {
            Director.extractProjectName(order)
        }
        val memory = sessionTranscript()
        val messages = mutableListOf(
            ChatTurn("system", AgentParser.SYSTEM.trimIndent()),
            ChatTurn(
                "user",
                buildString {
                    appendLine("Язык: русский. Размышляй и отвечай только по-русски.")
                    appendLine()
                    appendLine("ПЕРЕПИСКА СЕССИИ (не забывай, анализируй):")
                    appendLine(memory.ifBlank { "(пусто — это первое сообщение)" })
                    appendLine()
                    appendLine("ИСХОДНЫЙ ЗАКАЗ РЕЖИССЁРА (выполнять до конца, не подменять пустышкой):")
                    appendLine(order)
                    appendLine()
                    if (follow && task != order) {
                        appendLine("ТЕКУЩАЯ РЕПЛИКА (это уточнение/вопрос, НЕ новый проект):")
                        appendLine(task)
                        appendLine("Не создавай новый проект и не меняй имя. Продолжи исходный заказ или ответь на вопрос.")
                        appendLine()
                    }
                    appendLine(
                        if (!named.isNullOrBlank()) {
                            "Имя проекта, которое нужно использовать: $named"
                        } else {
                            "Имени нет. Придумай НОВОЕ имя из заказа. Никогда SkyArena. Не используй предлоги как имя."
                        },
                    )
                    appendLine("Открытый проект: ${projectName ?: "нет"}")
                    appendLine("Папка проекта: ${projectPath()}")
                    if (scene != null) {
                        appendLine("Сцена: ${scene?.name} ${scene?.dimension}")
                        val objs = scene?.objects.orEmpty()
                        appendLine("Объекты (${objs.size}): ${objs.joinToString { it.name + "/" + it.type }}")
                        val real = objs.filter { it.type !in listOf("Camera", "Light") }
                        if (real.isEmpty() && Director.wantsWorld(order)) {
                            appendLine("СЦЕНА ПУСТАЯ. Заказ требует мир — добавь землю, биомы, персонажей, ассеты, скрипты. Не заканчивай.")
                        }
                    }
                    if (!Director.wantsControls(order)) {
                        appendLine("Не вызывай controls.set. Режиссёр не просил тач-UI.")
                    }
                    if (!Director.wantsAnimation(order)) {
                        appendLine("Не добавляй анимации — их не просили.")
                    } else {
                        appendLine("Анимации просили — сделай через скрипты (движение/покачивание), не оставляй статичную пустышку.")
                    }
                    if (Director.wantsWorld(order)) {
                        appendLine("Это заказ на мир/игру. Пустая сцена (камера+свет) = провал. Собери видимый мир.")
                    }
                    appendLine("Уникальные ассеты через asset.create, сразу scene.add_object с material=путь.mat и mesh. Иначе файл есть, а сцена его не видит.")
                    appendLine("Звуки — asset.create kind=sound в Assets/Audio. Потом sound.play или api.playSound.")
                    if (pluginRecords.isEmpty()) {
                        appendLine("Плагинов нет. Для механик заказа вызови plugin.create с code (JS onPlay/onAddObject). Без плагина задумка не сдана.")
                    }
                },
            ),
        )
        viewModelScope.launch {
            try {
                var safety = 0
                while (!agentCancel && safety < 20) {
                    safety++
                    agentRound = safety
                    agentElapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
                    val liveId = System.nanoTime()
                    withContext(Dispatchers.Main) {
                        agentFeed += AgentEvent.Assistant(liveId, "", "", live = true)
                        streamTick++
                    }
                    val main = android.os.Handler(android.os.Looper.getMainLooper())
                    val llmStarted = System.currentTimeMillis()
                    val reply = withContext(Dispatchers.IO) {
                        ai.converseStream(
                            Provider.fromId(provider),
                            customModel.ifBlank { model },
                            messages,
                            customEndpoint,
                            abort = { agentCancel },
                        ) { delta ->
                            main.post {
                                val idx = agentFeed.indexOfFirst { it.id == liveId }
                                if (idx >= 0) {
                                    val cur = agentFeed[idx] as? AgentEvent.Assistant ?: return@post
                                    if (delta.reset) {
                                        agentFeed[idx] = cur.copy(text = "", raw = "", live = true)
                                    } else {
                                        val piece = StreamText.clean(delta.text)
                                        val reason = StreamText.clean(delta.thinking)
                                        if (piece.isEmpty() && reason.isEmpty()) return@post
                                        val rawAcc = StreamText.stripNullSpam(cur.raw + piece)
                                        val thinkAcc = StreamText.stripNullSpam(cur.thinking + reason)
                                        val view = AgentParser.display(rawAcc, thinkAcc)
                                        agentFeed[idx] = cur.copy(
                                            text = view.say,
                                            thinking = view.thinking.ifBlank { thinkAcc },
                                            raw = rawAcc,
                                            live = true,
                                        )
                                    }
                                    streamTick++
                                }
                            }
                        }
                    }.getOrElse {
                        pushCard("ошибка", "error", "{}", StreamText.humanError(it), 0, false)
                        return@launch
                    }
                    if (reply.provider.isNotBlank() || (reply.model.isNotBlank() && reply.model != model)) {
                        val nextProv = reply.provider.ifBlank { provider }.let {
                            if (it == "zen_direct" || it == "zendirect") "zen" else it
                        }
                        if (nextProv != provider || (reply.model.isNotBlank() && reply.model != model)) {
                            setRoute(nextProv, reply.model.ifBlank { model })
                            pushCard("модель", "fallback", "{}", "переключил на ${ModelCatalog.pretty(model)} · $nextProv", 0, true)
                        }
                    }
                    val llmMs = System.currentTimeMillis() - llmStarted
                    agentLastLlmMs = llmMs
                    agentTokens += reply.promptTokens + reply.completionTokens
                    val liveRaw = withContext(Dispatchers.Main) {
                        (agentFeed.firstOrNull { it.id == liveId } as? AgentEvent.Assistant)?.raw.orEmpty()
                    }
                    val raw = StreamText.stripNullSpam(reply.text.ifBlank { liveRaw.ifBlank { reply.thinking } })
                    messages += ChatTurn("assistant", raw)
                    val action = AgentParser.parse(raw)
                    val view = AgentParser.display(raw, reply.thinking)
                    withContext(Dispatchers.Main) {
                        val idx = agentFeed.indexOfFirst { it.id == liveId }
                        if (idx >= 0) {
                            val cur = agentFeed[idx] as? AgentEvent.Assistant
                            if (cur != null) {
                                val think = view.thinking.ifBlank { cur.thinking }.ifBlank { StreamText.stripNullSpam(reply.thinking) }
                                val shown = action.say.ifBlank { view.say }
                                agentFeed[idx] = cur.copy(
                                    text = shown,
                                    thinking = think,
                                    raw = raw,
                                    live = false,
                                    ms = llmMs,
                                )
                                streamTick++
                            }
                        }
                        persistFeed()
                    }
                    if (action.done && action.tool == null) {
                        pushCard("готово", "done", "{}", action.say.ifBlank { reply.text }, llmMs, true)
                        break
                    }
                    val tool = action.tool ?: "say"
                    val t0 = System.currentTimeMillis()
                    val result = runCatching { executeAgentTool(tool, action.args) }
                        .getOrElse { "ERROR: ${it.message}" }
                    val ms = System.currentTimeMillis() - t0
                    agentLastToolMs = ms
                    agentToolsUsed++
                    pushCard(tool, tool, action.args.toString(), result, ms, !result.startsWith("ERROR"))
                    if (action.done || tool == "done") {
                        if (action.say.isNotBlank()) pushCard("итог", "done", "{}", action.say, llmMs, true)
                        break
                    }
                    messages += ChatTurn("user", "TOOL_RESULT $tool:\n${result.take(4000)}")
                }
            } finally {
                agentRunning = false
                if (!agentCancel) agentStatus = "готов"
                agentElapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
                persistFeed()
            }
        }
    }

    private fun pushCard(title: String, tool: String, args: String, result: String, ms: Long, ok: Boolean) {
        val id = System.nanoTime()
        agentCards.add(
            0,
            AgentCard(
                id = id,
                title = title,
                tool = tool,
                args = args,
                result = result,
                ms = ms,
                ok = ok,
            ),
        )
        agentFeed += AgentEvent.Tool(id, title, tool, args, result, ms, ok, expanded = false)
        streamTick++
        persistFeed()
        log("$title · ${Director.formatMs(ms)}")
    }

    private fun createAsset(args: org.json.JSONObject): String {
        val p = current(false) ?: error("no project")
        val kind = args.optString("kind", "material").lowercase()
        val rawName = args.optString("name").ifBlank { "Asset" }
        val name = ProjectStore.sanitizeName(rawName).ifBlank { "Asset" }
        val color = args.optString("color").ifBlank { "#6ea8fe" }
        val accent = args.optString("accent").ifBlank { "#1a1a22" }
        val pattern = args.optString("pattern").ifBlank { "noise" }
        val mesh = args.optString("mesh").ifBlank { "Cube" }
        val written = mutableListOf<String>()
        when (kind) {
            "sound", "audio", "wav" -> {
                val path = "Assets/Audio/$name.wav"
                val freq = args.optDouble("freq", 660.0)
                store.writeBytes(p, path, AssetKitchen.wav(freq, 0.35)).getOrThrow()
                written += path
            }
            "mesh", "model" -> {
                val path = "Assets/Meshes/$name.obj"
                store.writeFile(p, path, AssetKitchen.meshObj(mesh)).getOrThrow()
                written += path
            }
            "texture" -> {
                val path = "Assets/Textures/$name.txt"
                store.writeFile(p, path, AssetKitchen.textureRecipe(name, color, pattern, accent)).getOrThrow()
                written += path
                val mat = "Assets/Materials/$name.mat"
                store.writeFile(p, mat, AssetKitchen.material(color, pattern, accent, mesh, name)).getOrThrow()
                written += mat
            }
            else -> {
                val mat = "Assets/Materials/$name.mat"
                store.writeFile(p, mat, AssetKitchen.material(color, pattern, accent, mesh, name)).getOrThrow()
                written += mat
                val meshPath = "Assets/Meshes/$name.obj"
                store.writeFile(p, meshPath, AssetKitchen.meshObj(mesh)).getOrThrow()
                written += meshPath
            }
        }
        written.forEach { path ->
            val before = ""
            val after = runCatching { store.resolve(p, path).takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
            recordWrite(path, "агент", before, after.take(2000))
        }
        applyAssetsToScene(p, name, written)
        refreshFiles()
        return "created ${written.joinToString()} — повесь material/mesh на объекты, иначе не видно"
    }

    private fun applyAssetsToScene(p: ProjectStore.Project, name: String, written: List<String>) {
        val currentScene = scene ?: return
        val mat = written.firstOrNull { it.endsWith(".mat") }
        val changed = currentScene.objects.filter { it.name.equals(name, true) || it.material.isBlank() && it.name.contains(name, true) }
        if (mat != null) {
            val text = runCatching { store.resolve(p, mat).readText() }.getOrNull().orEmpty()
            val (c, pat, acc) = AssetKitchen.parseMat(text)
            val targets = currentScene.objects.filter { it.name.equals(name, true) }
            targets.forEach { obj ->
                obj.material = mat
                if (c.isNotBlank()) obj.color = c
                obj.extra.put("pattern", pat)
                obj.extra.put("accent", acc)
                obj.extra.put("material", mat)
            }
        }
        if (changed.isNotEmpty() || (mat != null && currentScene.objects.any { it.name.equals(name, true) })) {
            persistScene(false)
        }
    }

    private fun executeAgentTool(tool: String, args: org.json.JSONObject): String {
        return when (tool) {
            "project.create" -> {
                if (Director.isFollowUp(lastUtterance) && !projectName.isNullOrBlank()) {
                    "SKIPPED: уже открыт $projectName — продолжай его, не создавай новый"
                } else {
                    val proposed = args.optString("name")
                    val name = Director.resolveName(lastDirectorTask, proposed, projectName)
                    if (!projectName.isNullOrBlank() && projectName.equals(name, true)) {
                        "уже открыт $projectName"
                    } else {
                        val type = args.optString("type", "3d")
                        val created = store.create(name, type).getOrThrow()
                        openProject(created.name, navigate = false)
                        "created ${created.name}"
                    }
                }
            }
            "project.list" -> store.projects().joinToString { it.name }.ifBlank { "(empty)" }
            "project.open" -> {
                openProject(args.optString("name"), navigate = false)
                "opened $projectName"
            }
            "project.path" -> projectPath()
            "project.seed_demo" -> {
                val demo = store.seedDemo()
                openProject(demo.name, navigate = false)
                "opened ${demo.name}"
            }
            "fs.list" -> {
                val p = current(false) ?: error("no project")
                store.files(p).joinToString("\n") { it.relativePath }.ifBlank { "(no files)" }
            }
            "fs.read" -> {
                val p = current(false) ?: error("no project")
                store.resolve(p, args.getString("path")).readText()
            }
            "fs.write", "fs.create" -> {
                val p = current(false) ?: error("no project")
                val path = args.getString("path")
                val content = args.optString("content")
                val before = runCatching { store.resolve(p, path).takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
                store.writeFile(p, path, content).getOrThrow()
                recordWrite(path, "агент", before, content)
                firePlugin("onSave", extraPath = path)
                refreshFiles()
                "wrote $path"
            }
            "scene.create" -> {
                createScene(args.optString("name", "Main"), args.optString("dimension", "3D"))
                "scene ok"
            }
            "scene.list" -> scenes.joinToString { it.name }.ifBlank { "(no scenes)" }
            "scene.add_object" -> addObjectFromArgs(args)
            "asset.create" -> createAsset(args)
            "controls.set" -> {
                if (!Director.wantsControls(lastDirectorTask)) {
                    "SKIPPED: director did not ask for touch UI / joystick / buttons"
                } else {
                    val p = current(false) ?: error("no project")
                    val body = if (args.has("items")) {
                        org.json.JSONObject().put("format", "mobileforge.controls.v1").put("items", args.get("items")).toString(2)
                    } else args.toString(2)
                    store.writeFile(p, "UI/Controls.json", body).getOrThrow()
                    refreshFiles()
                    "controls saved"
                }
            }
            "play.start" -> {
                startPlay()
                "play"
            }
            "plugin.create" -> {
                createPluginFromArgs(
                    args.optString("id"),
                    args.optString("title"),
                    args.optString("code").let { if (it == "null") "" else it },
                )
            }
            "sound.play", "audio.play" -> {
                val n = args.optString("name").ifBlank { args.optString("file") }
                val ok = soundBank?.play(n) == true
                if (ok) "played $n" else "нет звука $n (положи wav в Assets/Audio)"
            }
            "plugin.list" -> pluginRecords.joinToString { it.id + (if (it.enabled) "" else " (off)") }.ifBlank { "(no plugins)" }
            "plugin.run" -> runPluginMenu(args.optString("id"), args.optString("menu"))
            "say", "done" -> args.optString("say").ifBlank { "ok" }
            else -> error("unknown tool $tool")
        }
    }

    private fun refreshProviderFlags() {
        hasZen = secrets.has("zen_key")
        hasOr = secrets.has("openrouter_key")
        hasMcp = secrets.has("mcp_token")
        hasCustom = secrets.has("custom_key")
        hasOrca = secrets.has("orca_key")
        hasGemini = secrets.has("gemini_key_1") || secrets.has("gemini_key_2") || secrets.has("gemini_key_3")
        customEndpoint = prefs.getString("custom_endpoint", customEndpoint).orEmpty()
    }

    private fun current(alert: Boolean = true): ProjectStore.Project? =
        projectName?.let { store.find(it) } ?: run {
            if (alert && section != Section.Projects) notify("Сначала откройте проект")
            null
        }

    private fun buildPrompt(): String {
        val fileCtx = openPath?.let { "Active file: $it\n```\n${editorText.take(4000)}\n```\n" }.orEmpty()
        val sceneCtx = scene?.let {
            "Active scene ${it.name} ${it.dimension} objects: ${it.toJson().toString().take(2500)}\n"
        }.orEmpty()
        return """
            You are a code-only agent for MobileForge. The human is the director.
            Do not invent gameplay, art direction or architecture beyond the order.
            Write only code. Return complete files in fenced blocks with paths, e.g. ```Scripts/Player.cs
            There is NO default on-screen touch UI. If the director asks for controls/joystick/buttons/HUD,
            create UI/Controls.json:
            {"format":"mobileforge.controls.v1","items":[{"type":"joystick","anchor":"bl","action":"move"},{"type":"button","anchor":"br","label":"Jump","action":"jump"}]}
            Do not add touch controls unless explicitly asked.
            Command type: ${aiCommand.name}
            Preferred language: $aiLanguage (.cs first)
            Event hook if relevant: $aiEvent
            Project: ${projectName ?: "none"}
            $sceneCtx
            $fileCtx
            ORDER FROM DIRECTOR:
            $aiTask
        """.trimIndent()
    }
}
