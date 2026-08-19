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
import com.mobileforge.agent.AgentParser
import com.mobileforge.ai.AiGateway
import com.mobileforge.ai.AiResult
import com.mobileforge.ai.ChatTurn
import com.mobileforge.ai.Provider
import com.mobileforge.cloud.GhAccount
import com.mobileforge.cloud.GhRepo
import com.mobileforge.cloud.GhRun
import com.mobileforge.cloud.GitHubService
import com.mobileforge.engine.Completions
import com.mobileforge.engine.ControlLayout
import com.mobileforge.engine.CsTranspiler
import com.mobileforge.engine.GameRuntime
import com.mobileforge.engine.Orbit
import com.mobileforge.engine.Suggestion
import com.mobileforge.export.HtmlPreview
import com.mobileforge.mcp.McpHub
import com.mobileforge.mcp.McpServer
import com.mobileforge.mcp.McpTool
import com.mobileforge.plugins.PluginRegistry
import com.mobileforge.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class Section { Agent, Projects, Studio, Assets, Play, Cloud, Mcp, Ai, Settings }
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
    private var agentCancel = false
    private val sessionStarted = System.currentTimeMillis()
    val sessionLimitMs = 6L * 60 * 60 * 1000
    fun sessionElapsedMs(): Long = System.currentTimeMillis() - sessionStarted
    fun sessionLeftMs(): Long = (sessionLimitMs - sessionElapsedMs()).coerceAtLeast(0)

    val models: List<String>
        get() {
            val preset = when (provider) {
                "zen" -> listOf("laguna-s-2.1-free", "deepseek-v4-flash-free", "mimo-v2.5-free", "nemotron-3-ultra-free", "north-mini-code-free", "deepseek-v4-flash")
                "openrouter" -> listOf("openrouter/free", "google/gemma-3-27b-it:free", "meta-llama/llama-3.3-70b-instruct:free", "qwen/qwen3-30b-a3b:free")
                "orca" -> listOf("orcarouter/free", "deepseek/deepseek-v4-flash-free", "tencent/hy3-free", "deepseek/deepseek-v4-pro-free")
                "gemini" -> listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash")
                "mcp" -> listOf("Termux local agent model")
                else -> emptyList()
            }
            return (preset + listOfNotNull(customModel.ifBlank { null })).distinct()
        }

    init {
        refreshProjects()
        refreshProviderFlags()
        refreshGithub()
        refreshMcp()
        refreshPack()
        boundRepo = prefs.getString("gh_bound_repo", "").orEmpty()
        customEndpoint = prefs.getString("custom_endpoint", "").orEmpty()
        model = prefs.getString("${provider}_model", model).orEmpty().ifBlank { model }
        log("MobileForge 2.0 — телефон = превью/редактор, сборка = GitHub runner")
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
            Section.Studio, Section.Assets -> {
                refreshFiles()
                refreshScenes()
                refreshPack()
            }
            Section.Play -> {}
            Section.Settings -> refreshProviderFlags()
            Section.Cloud -> refreshGithub()
            Section.Mcp -> refreshMcp()
            Section.Ai, Section.Agent -> {}
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
        store.writeFile(project, path, editorText).fold(
            {
                dirty = false
                if (path.endsWith(".scene.json")) {
                    runCatching { scene = scenesStore.load(it.file) }
                }
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
            { openFile(path) },
            { notify(it.message ?: "Не создан") },
        )
    }

    fun deleteOpenFile() {
        val project = current() ?: return
        val path = openPath ?: return
        store.deleteFile(project, path).fold(
            {
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
                selected = it.objects.firstOrNull()?.name
                refreshScenes()
                notify("Сцена ${it.name}")
            },
            { notify(it.message ?: "Ошибка сцены") },
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
        scenesStore.save(currentScene)
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
        val currentScene = scene ?: return notify("Сначала сцена")
        val name = type + (currentScene.objects.size + 1)
        currentScene.objects += SceneObject(
            name = name,
            type = type,
            x = if (type == "Ground" || type == "Plane") 0f else 2f,
            y = if (type == "Camera") 5f else 1f,
            z = if (type == "Camera") 10f else 0f,
            color = when (type) {
                "Coin" -> "#f4c95d"
                "Enemy" -> "#ffb2c8"
                "Light" -> "#fff4cc"
                "Block" -> "#6ea8fe"
                else -> "#b69cff"
            },
            solid = type != "Camera" && type != "Light" && type != "Coin",
            mesh = SceneObject.defaultMesh(type),
            script = if (type == "Player") "Scripts/Player.cs" else "",
            lightType = if (type == "Light") "Directional" else "Directional",
        )
        selected = name
        persistScene(true)
        log("Добавлен $type $name")
    }

    fun deleteSelected() {
        val currentScene = scene ?: return
        currentScene.objects.removeAll { it.name == selected }
        selected = currentScene.objects.firstOrNull()?.name
        persistScene(true)
    }

    fun startPlay() {
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
        runtime = GameRuntime(currentScene, scripts).also { it.start() }
        log(
            if (playControls.items.isEmpty()) {
                "Play: сенсор не задан (AI может создать UI/Controls.json)"
            } else {
                "Play: ${playControls.items.size} виджет(ов) управления из проекта"
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

    fun plugins() = PluginRegistry.bundled

    fun toggleCard(id: Long) {
        val i = agentCards.indexOfFirst { it.id == id }
        if (i >= 0) agentCards[i] = agentCards[i].copy(expanded = !agentCards[i].expanded)
    }

    fun stopAgent() {
        agentCancel = true
        agentRunning = false
        agentStatus = "стоп"
        log("Агент остановлен")
    }

    fun submitAgent() {
        val task = agentInput.trim()
        if (task.isBlank() || agentRunning) return
        agentInput = ""
        runAgent(task)
    }

    fun runAgent(task: String) {
        agentCancel = false
        agentRunning = true
        agentStatus = "работа"
        agentRound = 0
        agentToolsUsed = 0
        val started = System.currentTimeMillis()
        val messages = mutableListOf(
            ChatTurn("system", AgentParser.SYSTEM.trimIndent()),
            ChatTurn(
                "user",
                buildString {
                    appendLine(task)
                    appendLine("Active project: ${projectName ?: "none"}")
                    if (scene != null) appendLine("Active scene: ${scene?.name} ${scene?.dimension}")
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
                    val reply = withContext(Dispatchers.IO) {
                        ai.converseResilient(Provider.fromId(provider), customModel.ifBlank { model }, messages, customEndpoint)
                    }.getOrElse {
                        val msg = it.message ?: "fail"
                        val human = if (com.mobileforge.ai.AiGateway.isTransient(it)) {
                            "Zen временно недоступен (upstream 503). Проект уже создан — отправьте задачу ещё раз."
                        } else msg
                        pushCard("ошибка", "error", "{}", human, 0, false)
                        return@launch
                    }
                    if (reply.model.isNotBlank() && reply.model != model) {
                        model = reply.model
                        pushCard("модель", "fallback", "{}", "переключил на ${reply.model}", 0, true)
                    }
                    agentTokens += reply.promptTokens + reply.completionTokens
                    messages += ChatTurn("assistant", reply.text)
                    val action = AgentParser.parse(reply.text)
                    if (action.done && action.tool == null) {
                        pushCard("готово", "done", "{}", action.say.ifBlank { reply.text }, 0, true)
                        break
                    }
                    val tool = action.tool ?: "say"
                    val t0 = System.currentTimeMillis()
                    val result = runCatching { executeAgentTool(tool, action.args) }
                        .getOrElse { "ERROR: ${it.message}" }
                    val ms = System.currentTimeMillis() - t0
                    agentToolsUsed++
                    pushCard(tool, tool, action.args.toString(), result, ms, !result.startsWith("ERROR"))
                    if (action.done || tool == "done") {
                        if (action.say.isNotBlank()) pushCard("итог", "done", "{}", action.say, 0, true)
                        break
                    }
                    messages += ChatTurn("user", "TOOL_RESULT $tool:\n${result.take(4000)}")
                }
            } finally {
                agentRunning = false
                if (!agentCancel) agentStatus = "готов"
                agentElapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
            }
        }
    }

    private fun pushCard(title: String, tool: String, args: String, result: String, ms: Long, ok: Boolean) {
        agentCards.add(
            0,
            AgentCard(
                id = System.nanoTime(),
                title = title,
                tool = tool,
                args = args,
                result = result,
                ms = ms,
                ok = ok,
            ),
        )
        log("$title · ${ms}ms")
    }

    private fun executeAgentTool(tool: String, args: org.json.JSONObject): String {
        return when (tool) {
            "project.create" -> {
                val name = args.optString("name")
                val type = args.optString("type", "3d")
                val created = store.create(name, type).getOrThrow()
                openProject(created.name, navigate = false)
                "created ${created.name}"
            }
            "project.list" -> store.projects().joinToString { it.name }.ifBlank { "(empty)" }
            "project.open" -> {
                openProject(args.optString("name"), navigate = false)
                "opened $projectName"
            }
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
                store.writeFile(p, args.getString("path"), args.optString("content")).getOrThrow()
                refreshFiles()
                "wrote ${args.getString("path")}"
            }
            "scene.create" -> {
                createScene(args.optString("name", "Main"), args.optString("dimension", "3D"))
                "scene ok"
            }
            "scene.list" -> scenes.joinToString { it.name }.ifBlank { "(no scenes)" }
            "scene.add_object" -> {
                addObject(args.optString("type", "Mesh"))
                "added"
            }
            "controls.set" -> {
                val p = current(false) ?: error("no project")
                val body = if (args.has("items")) {
                    org.json.JSONObject().put("format", "mobileforge.controls.v1").put("items", args.get("items")).toString(2)
                } else args.toString(2)
                store.writeFile(p, "UI/Controls.json", body).getOrThrow()
                refreshFiles()
                "controls saved"
            }
            "play.start" -> {
                startPlay()
                "play"
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
