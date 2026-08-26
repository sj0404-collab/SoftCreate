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

enum class Section { Agent, Projects, Files, Studio, Assets, Play, Cloud, Mcp, Ai, Settings, Changes, Plugins, Graph, Blender }
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
    var sceneEpoch by mutableStateOf(0)
    var selected by mutableStateOf<String?>(null)
    val orbit = Orbit()
    var runtime by mutableStateOf<GameRuntime?>(null)
    var playHud by mutableStateOf("")
    var graph by mutableStateOf<com.mobileforge.engine.VisualGraph?>(null)
    val editMesh = com.mobileforge.engine.EditMesh("Sculpt")
    var meshTick by mutableStateOf(0)
    var fileFilter by mutableStateOf("")
    private var meshUndo: String = ""
    var graphName by mutableStateOf("Player")
    var graphSelected by mutableStateOf<String?>(null)
    var playStandalone by mutableStateOf(false)
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
    var repoOnly by mutableStateOf(false)
    val modelStore = com.mobileforge.ai.ModelStore(prefs)
    val userModels = mutableStateListOf<com.mobileforge.ai.UserModel>()
    var newModelId by mutableStateOf("")
    var newModelLabel by mutableStateOf("")
    var newModelProvider by mutableStateOf("zen")
    var newModelFormat by mutableStateOf("json")
    var editModelUid by mutableStateOf<String?>(null)
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
        get() = (ModelCatalog.idsFor(provider) + userModels.map { it.id } + listOfNotNull(customModel.ifBlank { null })).distinct()

    fun reloadUserModels() {
        userModels.clear()
        userModels += modelStore.list()
    }

    fun saveUserModel() {
        val id = newModelId.trim()
        if (id.isBlank()) return notify("Нужен id модели")
        modelStore.upsert(
            com.mobileforge.ai.UserModel(
                uid = editModelUid ?: java.util.UUID.randomUUID().toString().take(8),
                providerId = newModelProvider.ifBlank { "zen" },
                id = id,
                label = newModelLabel.ifBlank { id },
                toolFormat = newModelFormat.ifBlank { "json" },
            ),
        )
        editModelUid = null
        newModelId = ""; newModelLabel = ""
        reloadUserModels()
        notify("Модель $id")
    }

    fun beginEditModel(uid: String) {
        val m = userModels.find { it.uid == uid } ?: return
        editModelUid = m.uid
        newModelId = m.id
        newModelLabel = m.label
        newModelProvider = m.providerId
        newModelFormat = m.toolFormat
    }

    fun deleteUserModel(uid: String) {
        modelStore.delete(uid)
        reloadUserModels()
    }

    fun toggleRepoOnly(on: Boolean) {
        repoOnly = on
        prefs.edit().putBoolean("repo_only", on).apply()
    }

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
        boundRepo = prefs.getString("gh_bound_repo", "").orEmpty()
        repoOnly = prefs.getBoolean("repo_only", false)
        reloadUserModels()
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
        log("MobileForge 2.18 — вкладки не рвут Play, физика xyz, Blender OBJ")
    }


    private fun feedFile(): File = File(getApplication<Application>().filesDir, "agent_feed.json")

    private fun persistFeed() {
        val snap = try { agentFeed.toList() } catch (_: Exception) { return }
        viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            snap.takeLast(150).forEach { ev ->
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
        section = next
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
            when (next) {
                Section.Projects -> withContext(Dispatchers.Main) { refreshProjects() }
                Section.Files, Section.Studio -> withContext(Dispatchers.Main) {
                    refreshFiles()
                    refreshScenes()
                }
                Section.Assets -> {
                    withContext(Dispatchers.Main) { refreshFiles() }
                    refreshPack()
                }
                Section.Ai -> withContext(Dispatchers.Main) {
                    refreshFiles()
                    scanIdeErrors()
                }
                Section.Changes -> withContext(Dispatchers.Main) {
                    refreshFiles()
                    reloadChanges()
                }
                Section.Plugins -> withContext(Dispatchers.Main) {
                    refreshFiles()
                    reloadPlugins()
                }
                Section.Graph -> withContext(Dispatchers.Main) { refreshFiles() }
                Section.Blender -> withContext(Dispatchers.Main) {
                    if (editMesh.verts.isEmpty()) editMesh.addPrimitive("cube")
                    meshTick++
                    refreshFiles()
                }
                Section.Settings -> withContext(Dispatchers.Main) { refreshProviderFlags() }
                Section.Cloud -> withContext(Dispatchers.Main) { refreshGithub() }
                Section.Mcp -> withContext(Dispatchers.Main) { refreshMcp() }
                else -> {}
            }
            }.onFailure { log("вкладка: ${it.message}") }
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

    private var packLoaded = false
    fun refreshPack() {
        if (packLoaded) return
        val collected = ArrayList<PackItem>()
        runCatching {
            val am = getApplication<Application>().assets
            fun walk(prefix: String) {
                val kids = am.list(prefix) ?: return
                if (kids.isEmpty() && prefix.contains('.')) {
                    collected += PackItem(prefix, prefix.substringAfterLast('.'))
                    return
                }
                kids.forEach { name ->
                    val path = if (prefix.isBlank()) name else "$prefix/$name"
                    val nested = am.list(path)
                    if (nested.isNullOrEmpty()) collected += PackItem(path, name.substringAfterLast('.'))
                    else walk(path)
                }
            }
            walk("StudioPack")
        }
        packLoaded = true
        val apply = Runnable {
            pack.clear()
            collected.forEach { pack += it }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) apply.run()
        else android.os.Handler(android.os.Looper.getMainLooper()).post(apply)
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
        val seed = com.mobileforge.engine.LangKit.seed(path)
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
        runCatching {
            val project = current(false) ?: return
            val loaded = scenesStore.scenes(project)
            scenes.clear()
            scenes += loaded
            if (scene == null) scene = scenes.firstOrNull()
        }.onFailure { log("сцены: ${it.message}") }
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
        runCatching {
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
            sceneEpoch++
            if (show) notify("Сцена сохранена")
        }.onFailure { log("сцена: ${it.message}") }
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

    fun meshAdd(kind: String) {
        meshUndo = editMesh.toObj()
        editMesh.addPrimitive(kind)
        meshTick++
    }

    fun meshNudge(dx: Float, dy: Float, dz: Float) {
        editMesh.nudge(dx, dy, dz); meshTick++
    }

    fun meshExtrude() {
        editMesh.extrude(); meshTick++
    }

    fun meshSmooth() {
        editMesh.smooth(); meshTick++
    }

    fun meshScale(s: Float) {
        editMesh.scale(s); meshTick++
    }

    fun meshClear() {
        meshUndo = editMesh.toObj()
        editMesh.clear(); meshTick++
    }

    fun meshUndoLast() {
        if (meshUndo.isBlank()) return
        val name = editMesh.name
        val restored = com.mobileforge.engine.EditMesh.parseObj(meshUndo, name)
        editMesh.clear()
        editMesh.verts.addAll(restored.verts)
        editMesh.faces.addAll(restored.faces)
        meshTick++
    }

    fun meshLoadFromSelected() {
        val obj = selectedObject() ?: return notify("Выберите объект в Сцене")
        val path = obj.asset.ifBlank { return notify("у объекта нет OBJ") }
        val p = current(false) ?: return
        val text = runCatching { store.resolve(p, path).readText() }.getOrNull() ?: return notify("нет $path")
        val loaded = com.mobileforge.engine.EditMesh.parseObj(text, obj.name)
        editMesh.clear()
        editMesh.name = loaded.name
        editMesh.verts.addAll(loaded.verts)
        editMesh.faces.addAll(loaded.faces)
        meshTick++
        notify("загружен $path")
    }

    fun meshSave(): String {
        val p = current() ?: return "ERROR: нет проекта"
        if (editMesh.verts.isEmpty()) return "ERROR: пустая сетка"
        val path = "Assets/Meshes/${ProjectStore.sanitizeName(editMesh.name)}.obj"
        store.writeFile(p, path, editMesh.toObj()).getOrThrow()
        refreshFiles()
        notify("OBJ $path")
        return path
    }

    fun meshApplyToSelected(): String {
        val obj = selectedObject() ?: return "ERROR: выберите объект в Сцене"
        val path = meshSave()
        if (path.startsWith("ERROR")) return path
        obj.asset = path
        obj.mesh = editMesh.toSceneMesh()
        persistScene(true)
        notify("меш на ${obj.name}")
        return "applied $path → ${obj.name}"
    }

    fun meshFromAi(kind: String, name: String, color: String): String {
        editMesh.clear()
        editMesh.name = name.ifBlank { kind }
        editMesh.color = color.ifBlank { "#8ab4ff" }
        editMesh.addPrimitive(kind.ifBlank { "cube" })
        meshTick++
        return meshSave()
    }

    fun newPlayerGraph() {
        graph = com.mobileforge.engine.VisualGraph.playerDefault()
        graphName = "Player"
        graphSelected = graph?.nodes?.firstOrNull()?.id
        notify("Граф игрока")
    }

    fun addGraphNode(kind: String) {
        val g = graph ?: com.mobileforge.engine.VisualGraph(graphName).also { graph = it }
        val id = "n${g.nodes.size + 1}"
        g.nodes += com.mobileforge.engine.GraphNode(id, kind, 20f, 40f * g.nodes.size)
        if (graphSelected != null) g.links += com.mobileforge.engine.GraphLink(graphSelected!!, id)
        graphSelected = id
        graph = g
    }

    fun replaceGraphNode(id: String, kind: String) {
        val g = graph ?: return
        g.nodes.find { it.id == id }?.kind = kind
        graph = g
    }

    fun removeGraphNode(id: String) {
        val g = graph ?: return
        g.nodes.removeAll { it.id == id }
        g.links.removeAll { it.from == id || it.to == id }
        if (graphSelected == id) graphSelected = null
        graph = g
    }

    fun linkToNext(id: String) {
        val g = graph ?: return
        val i = g.nodes.indexOfFirst { it.id == id }
        if (i < 0 || i + 1 >= g.nodes.size) return
        val to = g.nodes[i + 1].id
        if (g.links.none { it.from == id && it.to == to }) g.links += com.mobileforge.engine.GraphLink(id, to)
        graph = g
    }

    fun saveGraph() {
        val p = current() ?: return
        val g = graph ?: return notify("Нет графа")
        g.name = graphName.ifBlank { "Graph" }
        val path = "Assets/Graphs/${g.name}.json"
        store.writeFile(p, path, g.toJson().toString(2))
        refreshFiles()
        notify("Сохранён $path")
    }

    fun bindGraphToSelected() {
        val obj = selectedObject() ?: return notify("Выберите объект")
        val g = graph ?: return notify("Нет графа")
        saveGraph()
        obj.extra.put("graph", "Assets/Graphs/${g.name}.json")
        persistScene(true)
        notify("Граф на ${obj.name}")
    }

    fun addScriptToSelected(path: String) {
        val obj = selectedObject() ?: return
        val arr = obj.extra.optJSONArray("scripts") ?: org.json.JSONArray().also { obj.extra.put("scripts", it) }
        arr.put(path)
        if (obj.script.isBlank()) obj.script = path
        persistScene(true)
    }

    fun exportPlayer() {
        val p = current() ?: return
        val sc = scene ?: return notify("Нет сцены")
        val html = com.mobileforge.export.HtmlPreview.player(sc, playControls)
        store.writeFile(p, "Export/index.html", html)
        store.writeFile(p, "Export/game.json", sc.toJson().toString(2))
        refreshFiles()
        notify("Игрок: Export/index.html — откройте или залейте в Cloud")
        log("Player export ${sc.name}")
    }

    fun addComponentToSelected(type: String) {
        val obj = selectedObject() ?: return notify("Нет объекта")
        com.mobileforge.engine.addComponent(obj.extra, type)
        persistScene(true)
        notify("+ $type")
    }

    fun setComponentField(index: Int, key: String, value: String) {
        val obj = selectedObject() ?: return
        val arr = obj.extra.optJSONArray("components") ?: return
        val o = arr.optJSONObject(index) ?: return
        value.toDoubleOrNull()?.let { o.put(key, it) } ?: o.put(key, value)
        persistScene(false)
    }

    fun savePrefab(name: String): String {
        val p = current(false) ?: return "ERROR: no project"
        val obj = scene?.objects?.firstOrNull { it.name == name } ?: return "ERROR: нет $name"
        val path = "Assets/Prefabs/${obj.name}.json"
        store.writeFile(p, path, obj.toJson().toString(2)).getOrThrow()
        refreshFiles()
        return "prefab $path"
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
            script = args.optString("script").ifBlank { if (type == "Player") "Scripts/Player.cs" else "" },
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
            val file = listOf("UI/Controls.json", "Assets/UI/Controls.json")
                .map { store.resolve(project, it) }.firstOrNull { it.isFile }
            if (file != null) ControlLayout.parse(file.readText()) else ControlLayout.EMPTY
        }.getOrDefault(ControlLayout.EMPTY)
        if (playControls.items.isEmpty() && Director.wantsControls(lastSubstantialTask.ifBlank { lastDirectorTask })) {
            playControls = ControlLayout.defaultPad()
            store.writeFile(project, "UI/Controls.json", ControlLayout.toJson(playControls))
        }
        runtime?.stop()
        val bank = soundBank ?: SoundBank(getApplication()).also { soundBank = it }
        val wavs = runCatching { bank.loadFrom(project.directory) }.getOrDefault(emptyList())
        val prefabs = HashMap<String, String>()
        store.files(project).filter { it.relativePath.startsWith("Assets/Prefabs/") }.forEach { f ->
            runCatching { prefabs[f.file.nameWithoutExtension] = f.file.readText() }
        }
        val graphs = HashMap<String, com.mobileforge.engine.VisualGraph>()
        store.files(project).filter { it.relativePath.startsWith("Assets/Graphs/") }.forEach { f ->
            runCatching { graphs[f.relativePath] = com.mobileforge.engine.VisualGraph.parse(f.file.readText()) }
        }
        currentScene.objects.forEach { obj ->
            val arr = obj.extra.optJSONArray("scripts")
            if (arr != null) for (i in 0 until arr.length()) {
                val pth = arr.optString(i)
                if (pth.isNotBlank() && pth !in scripts) {
                    runCatching { scripts[pth] = store.resolve(project, pth).readText() }
                }
            }
        }
        runtime = GameRuntime(
            currentScene,
            scripts,
            onSound = { name -> runCatching { bank.play(name) } },
            prefabs = prefabs,
            graphs = graphs,
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
        playHud = "${projectName ?: ""}  •  score ${rt.score}  •  ${rt.actors.count { it.alive }} obj" +
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
                    val kind = Director.classify(order)
                    appendLine("ТИП ЗАКАЗА: $kind")
                    if (kind == Director.Kind.Github) {
                        appendLine("Это GitHub. Не project.create / scene.add_object. github.repos / github.ls / github.read / github.write.")
                        appendLine("Репо: ${boundRepo.ifBlank { "нет" }} PAT: ${if (github.activeToken().isNullOrBlank()) "НЕТ" else "есть"}")
                    }
                    appendLine("Скрипты только Scripts/*.cs. .tsx/.jsx запрещены.")
                    if (Director.wantsControls(order)) appendLine("Просили управление — controls.set с joystick+кнопками.")
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
                    if (Director.wants2D(order)) {
                        appendLine("Это 2D. project.create type=2d, scene.create dimension=2D. Не 3D. Имя не «любую».")
                    }
                    appendLine("Имя проекта не предлог и не «любую/для/игра». Если не назвали — выдумай нормальное имя.")
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
                        agentFeed.indices.forEach { i ->
                            val ev = agentFeed[i]
                            if (ev is AgentEvent.Assistant && ev.live) {
                                agentFeed[i] = ev.copy(live = false)
                            }
                        }
                        agentFeed += AgentEvent.Assistant(liveId, "", "", live = true)
                        streamTick++
                    }
                    val main = android.os.Handler(android.os.Looper.getMainLooper())
                    val llmStarted = System.currentTimeMillis()
                    val accRaw = StringBuilder()
                    val accThink = StringBuilder()
                    val accLock = Any()
                    var lastUi = 0L
                    var uiPosted = false
                    var liveDone = false
                    var paintLater: Runnable = Runnable { }
                    fun paintLive(force: Boolean) {
                        if (liveDone) return
                        val now = System.currentTimeMillis()
                        if (!force && now - lastUi < 90L) {
                            if (!uiPosted) {
                                uiPosted = true
                                main.postDelayed(paintLater, 90L)
                            }
                            return
                        }
                        lastUi = now
                        val rawSnap: String
                        val thinkSnap: String
                        synchronized(accLock) {
                            rawSnap = accRaw.toString()
                            thinkSnap = accThink.toString()
                        }
                        main.post {
                            if (liveDone) return@post
                            val idx = agentFeed.indexOfFirst { it.id == liveId }
                            if (idx < 0) return@post
                            val cur = agentFeed[idx] as? AgentEvent.Assistant ?: return@post
                            if (!cur.live) return@post
                            val cutBrace = rawSnap.indexOf('{')
                            val cutTool = rawSnap.indexOf("<tool")
                            val cut = listOf(cutBrace, cutTool).filter { it >= 0 }.minOrNull() ?: -1
                            val prose = if (cut >= 0) rawSnap.take(cut).trim() else rawSnap.trim()
                            val sayHit = Regex("\"say\"\\s*:\\s*\"([^\"]*)\"").find(rawSnap)?.groupValues?.get(1).orEmpty()
                            val think = thinkSnap.ifBlank { prose }.ifBlank { sayHit }.takeLast(8000)
                            agentFeed[idx] = cur.copy(
                                text = sayHit,
                                thinking = think,
                                raw = rawSnap,
                                live = true,
                            )
                        }
                    }
                    paintLater = Runnable {
                        uiPosted = false
                        if (!liveDone) paintLive(true)
                    }
                    val streamed = withContext(Dispatchers.IO) {
                        ai.converseStream(
                            Provider.fromId(provider),
                            customModel.ifBlank { model },
                            messages,
                            customEndpoint,
                            abort = { agentCancel },
                        ) { delta ->
                            if (delta.reset) {
                                synchronized(accLock) { accRaw.setLength(0); accThink.setLength(0) }
                                paintLive(true)
                                return@converseStream
                            }
                            val piece = StreamText.clean(delta.text)
                            val reason = StreamText.clean(delta.thinking)
                            if (piece.isEmpty() && reason.isEmpty()) return@converseStream
                            synchronized(accLock) {
                                if (piece.isNotEmpty()) accRaw.append(piece)
                                if (reason.isNotEmpty()) accThink.append(reason)
                            }
                            paintLive(false)
                        }
                    }
                    var replyResult = streamed
                    if (replyResult.isFailure) {
                        val err = replyResult.exceptionOrNull()
                        replyResult = withContext(Dispatchers.IO) {
                            ai.converseResilient(
                                Provider.fromId(provider),
                                customModel.ifBlank { model },
                                messages,
                                customEndpoint,
                            )
                        }
                        if (replyResult.isSuccess) {
                            pushCard("модель", "fallback", "{}", "стрим упал — продолжаю без стрима", 0, true)
                        } else {
                            val nxt = ModelCatalog.plan(Provider.fromId(provider), "auto") { pid ->
                                when (pid) {
                                    Provider.ZEN_DIRECT -> true
                                    Provider.OPENROUTER -> hasOr
                                    Provider.ORCA -> hasOrca
                                    Provider.GEMINI -> hasGemini
                                    else -> false
                                }
                            }.firstOrNull { it.second != model }
                            if (false && nxt != null) {
                                pushCard(
                                    "модель",
                                    "pin",
                                    "{}",
                                    StreamText.humanError(err ?: Exception("ошибка модели")) + " — выбранную модель не меняю.",
                                    0,
                                    false,
                                )
                                liveDone = true
                                main.removeCallbacks(paintLater)
                                withContext(Dispatchers.Main) {
                                    val idx = agentFeed.indexOfFirst { it.id == liveId }
                                    val cur = if (idx >= 0) agentFeed[idx] as? AgentEvent.Assistant else null
                                    if (idx >= 0 && cur != null && cur.live) {
                                        agentFeed[idx] = cur.copy(live = false)
                                    }
                                }
                                continue
                            }
                            liveDone = true
                            main.removeCallbacks(paintLater)
                            pushCard("ошибка", "error", "{}", StreamText.humanError(err ?: Exception("ошибка модели")), 0, false)
                            break
                        }
                    }
                    val reply = replyResult.getOrThrow()
                    if (reply.provider.isNotBlank() || (reply.model.isNotBlank() && reply.model != model)) {
                        val nextProv = reply.provider.ifBlank { provider }.let {
                            if (it == "zen_direct" || it == "zendirect") "zen" else it
                        }
                        if (reply.model.isNotBlank() && reply.model != model) {
                            pushCard("модель", "pin", "{}", "ответ как ${reply.model}, оставляю выбранную $model", 0, true)
                        }
                    }
                    paintLive(true)
                    liveDone = true
                    main.removeCallbacks(paintLater)
                    val llmMs = System.currentTimeMillis() - llmStarted
                    agentLastLlmMs = llmMs
                    agentTokens += reply.promptTokens + reply.completionTokens
                    val accSnap = synchronized(accLock) { accRaw.toString() }
                    val liveRaw = withContext(Dispatchers.Main) {
                        (agentFeed.firstOrNull { it.id == liveId } as? AgentEvent.Assistant)?.raw.orEmpty()
                    }
                    val raw = StreamText.stripNullSpam(reply.text.ifBlank { accSnap.ifBlank { liveRaw.ifBlank { reply.thinking } } })
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
                    if (action.tool == null && !action.done) {
                        messages += ChatTurn(
                            "user",
                            "Это не вызов инструмента. Запрещён XML <tool_call>. Ответь ОДНИМ JSON: {\"tool\":\"project.list\",\"args\":{},\"say\":\"кратко\"}. Без markdown.",
                        )
                        continue
                    }
                    if (action.done && action.tool == null) {
                        val say = AgentParser.humanText(action.say.ifBlank { reply.text })
                        if (say.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                val idx = agentFeed.indexOfFirst { it.id == liveId }
                                if (idx >= 0) {
                                    val cur = agentFeed[idx] as? AgentEvent.Assistant
                                    if (cur != null) agentFeed[idx] = cur.copy(text = say, live = false, ms = llmMs)
                                }
                            }
                        }
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
            "blender", "bpy", "blend" -> {
                val shape = args.optString("shape").ifBlank { mesh }
                val py = "Blender/$name.py"
                store.writeFile(p, py, com.mobileforge.engine.LangKit.blenderPy(name, shape, color)).getOrThrow()
                written += py
                val objPath = "Assets/Meshes/$name.obj"
                store.writeFile(p, objPath, AssetKitchen.meshObj(shape)).getOrThrow()
                written += objPath
                val mat = "Assets/Materials/$name.mat"
                store.writeFile(p, mat, AssetKitchen.material(color, pattern, accent, shape, "blender:$name")).getOrThrow()
                written += mat
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
        return when {
            written.any { it.endsWith(".wav") } && written.none { it.endsWith(".mat") } ->
                "created ${written.joinToString()} — звук в Assets/Audio, play.start его подхватит"
            else ->
                "created ${written.joinToString()} — повесь material/mesh на объекты"
        }
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

    private fun sanitizeScriptPath(path: String, content: String): String {
        val p = path.replace('\\', '/').trimStart('/')
        val body = content.lowercase()
        if (p.endsWith(".tsx", true) || p.endsWith(".jsx", true) || p.endsWith(".ts", true) ||
            "from 'react'" in body || "from \"react\"" in body
        ) {
            return "ERROR: только Scripts/*.cs (ForgeBehaviour). TSX/React не принимаются."
        }
        return p
    }

    private fun executeAgentTool(tool: String, args: org.json.JSONObject): String {
        val kind = Director.classify(lastDirectorTask.ifBlank { lastUtterance })
        if (kind == Director.Kind.Github && tool in setOf("project.create", "project.seed_demo", "scene.add_object", "scene.create")) {
            return "SKIPPED: заказ про GitHub. Используй github.ls / github.read / github.write."
        }
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
                        val type = if (Director.wants2D(lastDirectorTask)) "2d" else args.optString("type", "3d")
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
                val p = current(false) ?: error("no project")
                val parsed = ControlLayout.parse(
                    if (args.has("items")) org.json.JSONObject().put("items", args.get("items")).toString() else args.toString(),
                )
                val layout = if (parsed.items.isEmpty()) ControlLayout.defaultPad() else parsed
                store.writeFile(p, "UI/Controls.json", ControlLayout.toJson(layout)).getOrThrow()
                playControls = layout
                refreshFiles()
                "controls saved ${layout.items.size}"
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
            "component.add", "scene.add_component" -> {
                val n = args.optString("name").ifBlank { selected.orEmpty() }
                val obj = scene?.objects?.firstOrNull { it.name == n } ?: error("нет объекта $n")
                com.mobileforge.engine.addComponent(obj.extra, args.optString("type").ifBlank { args.optString("component") }, args)
                persistScene(true)
                "added component → $n"
            }
            "component.list" -> com.mobileforge.engine.EngineKit.allTypes().joinToString()
            "graph.create" -> {
                newPlayerGraph()
                if (args.optString("name").isNotBlank()) graphName = args.optString("name")
                saveGraph()
                "graph Assets/Graphs/$graphName.json"
            }
            "graph.bind" -> {
                bindGraphToSelected()
                "bound"
            }
            "export.player" -> {
                exportPlayer()
                "Export/index.html"
            }
            "blender.generate", "asset.blender", "mesh.add" -> {
                val n = args.optString("name").ifBlank { "Sculpt" }
                val shape = args.optString("shape").ifBlank { args.optString("kind").ifBlank { "cube" } }
                if (shape == "blender") {
                    createAsset(args.put("kind", "blender"))
                }
                meshFromAi(shape, n, args.optString("color", "#8ab4ff"))
            }
            "mesh.save" -> meshSave()
            "mesh.apply" -> meshApplyToSelected()
            "mesh.clear" -> { meshClear(); "cleared" }
            "prefab.save" -> savePrefab(args.optString("name").ifBlank { selected.orEmpty() })
            "prefab.spawn" -> {
                val p = current(false) ?: error("no project")
                val name = args.optString("name")
                val file = store.resolve(p, "Assets/Prefabs/$name.json")
                if (!file.isFile) error("нет префаба $name")
                val obj = com.mobileforge.SceneObject.fromJson(org.json.JSONObject(file.readText()))
                obj.x = args.optDouble("x", obj.x.toDouble()).toFloat()
                obj.y = args.optDouble("y", obj.y.toDouble()).toFloat()
                obj.z = args.optDouble("z", obj.z.toDouble()).toFloat()
                val sc = scene ?: error("no scene")
                if (sc.objects.any { it.name == obj.name }) obj.name = obj.name + "_" + sc.objects.size
                sc.objects += obj
                persistScene(true)
                "spawned ${obj.name}"
            }
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
