package com.mobileforge

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileforge.ai.AiGateway
import com.mobileforge.ai.AiResult
import com.mobileforge.ai.Provider
import com.mobileforge.engine.Completions
import com.mobileforge.engine.CsTranspiler
import com.mobileforge.engine.GameRuntime
import com.mobileforge.engine.Orbit
import com.mobileforge.engine.Suggestion
import com.mobileforge.export.HtmlPreview
import com.mobileforge.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class Section { Projects, Studio, Scenes, Play, Ai, Settings }

data class ProjectItem(val name: String, val type: String)
data class FileItem(val path: String, val name: String)
data class ProposalFile(val path: String, val content: String, val previous: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    private val scenesStore = SceneStore()
    private val secrets = SecretStore(app)
    private val ai = AiGateway(secrets)
    private val prefs: SharedPreferences =
        app.getSharedPreferences("mobileforge.providers", android.content.Context.MODE_PRIVATE)

    var section by mutableStateOf(Section.Projects)
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
    var provider by mutableStateOf("zen")
    var model by mutableStateOf("deepseek-v4-flash")
    var customEndpoint by mutableStateOf("")
    var customModel by mutableStateOf("")
    var aiEvent by mutableStateOf("ON_UPDATE")
    var aiLanguage by mutableStateOf("JavaScript")
    var aiTask by mutableStateOf("Создай компонент движения персонажа и подбор монет.")
    var aiOut by mutableStateOf("Готов к задаче. Контекст возьмётся из открытого проекта и сцены.")
    var aiBusy by mutableStateOf(false)
    var proposal = mutableStateListOf<ProposalFile>()
    var zenKey by mutableStateOf("")
    var orKey by mutableStateOf("")
    var mcpKey by mutableStateOf("")
    var customKey by mutableStateOf("")
    var hasZen by mutableStateOf(false)
    var hasOr by mutableStateOf(false)
    var hasMcp by mutableStateOf(false)
    var hasCustom by mutableStateOf(false)
    var dialog by mutableStateOf<String?>(null)
    var dialogValue by mutableStateOf("")
    var importText by mutableStateOf("")

    val models: List<String>
        get() {
            val preset = when (provider) {
                "zen" -> listOf("deepseek-v4-flash", "deepseek-v4-flash-free", "mimo-v2.5-free", "nemotron-3-ultra-free", "north-mini-code-free", "laguna-s-2.1-free")
                "openrouter" -> listOf("openrouter/free", "google/gemma-3-27b-it:free", "meta-llama/llama-3.3-70b-instruct:free", "qwen/qwen3-30b-a3b:free")
                "mcp" -> listOf("Termux local agent model")
                else -> emptyList()
            }
            return (preset + listOfNotNull(customModel.ifBlank { null })).distinct()
        }

    init {
        refreshProjects()
        refreshProviderFlags()
        customEndpoint = prefs.getString("custom_endpoint", "").orEmpty()
        model = prefs.getString("${provider}_model", model).orEmpty().ifBlank { model }
    }

    fun notify(text: String) {
        toast = text
    }

    fun go(next: Section) {
        if (dirty && section == Section.Studio && next != Section.Studio) {
            notify("Сначала сохраните файл или отмените правки")
        }
        if (next != Section.Play) runtime?.stop()
        section = next
        when (next) {
            Section.Projects -> refreshProjects()
            Section.Studio -> {
                refreshFiles()
                refreshScenes()
            }
            Section.Scenes -> refreshScenes()
            Section.Play -> startPlay()
            Section.Settings -> refreshProviderFlags()
            Section.Ai -> {}
        }
    }

    fun back(): Boolean {
        return when (section) {
            Section.Play -> { go(Section.Studio); true }
            Section.Projects -> false
            else -> { go(Section.Projects); true }
        }
    }

    fun refreshProjects() {
        projects.clear()
        store.projects().forEach { p ->
            val type = store.meta(p).optString("type", "3d")
            projects += ProjectItem(p.name, type)
        }
    }

    fun openProject(name: String) {
        projectName = name
        dirty = false
        openPath = null
        editorText = ""
        refreshFiles()
        refreshScenes()
        scene = scenes.firstOrNull()
        selected = scene?.objects?.firstOrNull()?.name
        notify("Проект: $name")
        go(Section.Studio)
    }

    fun createProject(raw: String, type: String) {
        store.create(raw, type).fold(
            { openProject(it.name) },
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
        val project = current() ?: return
        files.clear()
        store.files(project).forEach { files += FileItem(it.relativePath, it.file.name) }
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
        val (start, prefix) = Completions.prefixAt(text, cursorPos)
        val extras = files.map { it.name.substringBeforeLast('.') } +
            (scene?.objects?.map { it.name } ?: emptyList())
        suggestions.clear()
        if (prefix.length >= 1) suggestions += Completions.suggest(prefix, openPath, extras)
        if (start == cursorPos) suggestions.clear()
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
            path.endsWith(".js") -> "function onUpdate(api, dt) {\n  \n}\n"
            path.endsWith(".cs") -> "public class Component {\n    void Update() {\n        \n    }\n}\n"
            path.endsWith(".json") -> "{\n  \n}\n"
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
        val project = current() ?: return
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
        val project = current() ?: return
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
            x = if (type == "Ground") 0f else 2f,
            y = if (type == "Camera") 5f else 1f,
            z = if (type == "Camera") 10f else 0f,
            color = when (type) {
                "Coin" -> "#f4c95d"
                "Enemy" -> "#ffb2c8"
                "Light" -> "#fff4cc"
                else -> "#b69cff"
            },
            solid = type != "Camera" && type != "Light" && type != "Coin",
        )
        selected = name
        persistScene(true)
    }

    fun deleteSelected() {
        val currentScene = scene ?: return
        currentScene.objects.removeAll { it.name == selected }
        selected = currentScene.objects.firstOrNull()?.name
        persistScene(true)
    }

    fun startPlay() {
        val project = current() ?: return notify("Нет проекта").let {}
        if (scene == null) refreshScenes()
        val currentScene = scene ?: scenes.firstOrNull() ?: return notify("Нет сцены")
        val scripts = HashMap<String, String>()
        currentScene.objects.forEach { obj ->
            if (obj.script.isNotBlank()) {
                runCatching {
                    scripts[obj.script] = store.resolve(project, obj.script).readText()
                }
            }
        }
        runtime?.stop()
        runtime = GameRuntime(currentScene, scripts).also { it.start() }
        section = Section.Play
    }

    fun tickPlay(dt: Float) {
        val rt = runtime ?: return
        rt.step(dt)
        rt.input.jump = false
        playHud = "${projectName ?: ""}  •  score ${rt.score}" +
            (rt.log.firstOrNull()?.let { "  •  $it" } ?: "")
    }

    fun generateAi() {
        if (aiTask.isBlank()) return notify("Опишите задачу")
        aiBusy = true
        aiOut = "Генерация…"
        val prompt = buildPrompt()
        val endpoint = customEndpoint
        val chosen = customModel.ifBlank { model }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ai.send(Provider.fromId(provider), chosen, prompt, endpoint)
                }.getOrElse { AiResult.Failure(it.message ?: "AI error") }
            }
            aiBusy = false
            when (result) {
                is AiResult.Success -> {
                    aiOut = result.text
                    val parsed = CsTranspiler.parseProposal(result.text).map { (path, content) ->
                        val resolved = path.ifBlank { CsTranspiler.guessPath(aiLanguage, aiEvent, openPath) }
                        val previous = current()?.let { p ->
                            runCatching { store.resolve(p, resolved).takeIf { it.isFile }?.readText() }.getOrNull()
                        }.orEmpty()
                        ProposalFile(resolved, content, previous)
                    }
                    proposal.clear()
                    proposal += parsed
                    notify("Proposal готов — проверьте Review")
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
        if (proposal.isEmpty()) return notify("Сначала Generate")
        proposal.forEach { file ->
            store.writeFile(project, file.path, file.content).onFailure {
                notify("${file.path}: ${it.message}")
            }
        }
        notify("Применено: ${proposal.size}")
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
        if (customEndpoint.isNotBlank()) {
            if (!customEndpoint.startsWith("https://")) {
                notify("Custom endpoint только HTTPS")
            } else {
                secrets.put("custom_endpoint", customEndpoint)
            }
        }
        prefs.edit()
            .putString("custom_endpoint", customEndpoint)
            .putString("${provider}_model", customModel.ifBlank { model })
            .apply()
        zenKey = ""; orKey = ""; mcpKey = ""; customKey = ""
        refreshProviderFlags()
        notify("Ключи сохранены в Keystore")
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

    private fun refreshProviderFlags() {
        hasZen = secrets.has("zen_key")
        hasOr = secrets.has("openrouter_key")
        hasMcp = secrets.has("mcp_token")
        hasCustom = secrets.has("custom_key")
        customEndpoint = prefs.getString("custom_endpoint", customEndpoint).orEmpty()
    }

    private fun current(): ProjectStore.Project? =
        projectName?.let { store.find(it) } ?: run {
            if (section != Section.Projects) notify("Сначала откройте проект")
            null
        }

    private fun buildPrompt(): String {
        val fileCtx = openPath?.let { "Active file: $it\n```\n${editorText.take(4000)}\n```\n" }.orEmpty()
        val sceneCtx = scene?.let {
            "Active scene ${it.name} ${it.dimension} objects: ${it.toJson().toString().take(2500)}\n"
        }.orEmpty()
        return """
            You are the MobileForge native game-engine coding agent.
            Return complete file contents in fenced code blocks.
            Put the file path in the fence tag, for example ```Scripts/Player.js
            Event hook: $aiEvent
            Language: $aiLanguage
            Project: ${projectName ?: "none"}
            $sceneCtx
            $fileCtx
            Task: $aiTask
        """.trimIndent()
    }
}
