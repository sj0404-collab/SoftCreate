package com.mobileforge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectStore(context: Context) {
    data class Project(val name: String, val directory: File)
    data class SourceFile(val relativePath: String, val file: File)

    val root: File = resolveRoot(context)

    private val allowed = setOf(
        "cs", "cpp", "c", "h", "hpp", "json", "xml", "glsl", "vert", "frag",
        "txt", "md", "lua", "js", "kt", "html", "css", "tsv", "csv", "scene",
        "obj", "gltf", "mat", "prefab", "plugin", "asmdef",
    )

    fun projects(): List<Project> =
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.map { Project(it.name, it) }
            ?: emptyList()

    fun find(name: String): Project? = projects().firstOrNull { it.name == sanitizeName(name) }

    fun create(name: String, type: String = "3d"): Result<Project> = runCatching {
        val safe = sanitizeName(name)
        require(safe.isNotBlank()) { "Enter a project name." }
        val dir = File(root, safe)
        require(!dir.exists()) { "A project with this name already exists." }
        dir.mkdirs()
        listOf("Scripts", "Scenes", "Assets", "Models", "Materials", "Prefabs", "Plugins").forEach {
            File(dir, it).mkdirs()
        }
        val dimension = if (type.equals("2d", true)) "2D" else "3D"
        File(dir, "project.json").writeText(
            """
            {
              "name": "$safe",
              "engine": "MobileForge",
              "version": "1.0.0",
              "type": "${dimension.lowercase()}",
              "mainScene": "Main"
            }
            """.trimIndent() + "\n",
        )
        File(dir, "README.md").writeText(
            """
            # $safe

            MobileForge project. Open Studio to edit scripts and scenes, then press Play.
            """.trimIndent() + "\n",
        )
        writeStarter(dir, safe, dimension)
        Project(safe, dir)
    }

    fun delete(name: String): Result<Unit> = runCatching {
        val project = find(name) ?: error("Project not found.")
        project.directory.deleteRecursively()
    }

    fun rename(oldName: String, newName: String): Result<Project> = runCatching {
        val project = find(oldName) ?: error("Project not found.")
        val safe = sanitizeName(newName)
        require(safe.isNotBlank()) { "Enter a project name." }
        val target = File(root, safe)
        require(!target.exists()) { "A project with this name already exists." }
        require(project.directory.renameTo(target)) { "Rename failed." }
        val meta = File(target, "project.json")
        if (meta.exists()) {
            val json = JSONObject(meta.readText())
            json.put("name", safe)
            meta.writeText(json.toString(2) + "\n")
        }
        Project(safe, target)
    }

    fun files(project: Project): List<SourceFile> =
        project.directory.walkTopDown()
            .filter { it.isFile && allowed.contains(it.extension.lowercase()) }
            .map { SourceFile(it.relativeTo(project.directory).invariantSeparatorsPath, it) }
            .sortedBy { it.relativePath.lowercase() }
            .toList()

    fun read(source: SourceFile): String = source.file.readText()

    fun save(source: SourceFile, content: String) {
        source.file.parentFile?.mkdirs()
        source.file.writeText(content)
    }

    fun resolve(project: Project, relativePath: String): File {
        val path = relativePath.trim().replace('\\', '/')
        require(path.isNotBlank() && !path.startsWith("/") && ".." !in path) {
            "Use a relative path inside the project."
        }
        val target = File(project.directory, path).canonicalFile
        val rootPath = project.directory.canonicalPath + File.separator
        require(target.path.startsWith(rootPath) || target.path == project.directory.canonicalPath) {
            "Invalid path."
        }
        return target
    }

    fun createFile(project: Project, relativePath: String, content: String = ""): Result<SourceFile> =
        runCatching {
            val target = resolve(project, relativePath)
            require(!target.exists()) { "File already exists." }
            target.parentFile?.mkdirs()
            target.writeText(content)
            SourceFile(relativePath.replace('\\', '/'), target)
        }

    fun writeFile(project: Project, relativePath: String, content: String): Result<SourceFile> =
        runCatching {
            val target = resolve(project, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
            SourceFile(relativePath.replace('\\', '/'), target)
        }

    fun deleteFile(project: Project, relativePath: String): Result<Unit> = runCatching {
        val target = resolve(project, relativePath)
        require(target.exists()) { "File not found." }
        require(target.deleteRecursively()) { "Delete failed." }
    }

    fun renameFile(project: Project, from: String, to: String): Result<SourceFile> = runCatching {
        val source = resolve(project, from)
        require(source.exists()) { "File not found." }
        val target = resolve(project, to)
        require(!target.exists()) { "Target already exists." }
        target.parentFile?.mkdirs()
        require(source.renameTo(target)) { "Rename failed." }
        SourceFile(to.replace('\\', '/'), target)
    }

    fun exportBundle(project: Project): JSONObject {
        val files = JSONObject()
        project.directory.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(project.directory).invariantSeparatorsPath
            files.put(rel, file.readText())
        }
        return JSONObject()
            .put("format", "mobileforge.project.v1")
            .put("name", project.name)
            .put("files", files)
    }

    fun importBundle(bundle: JSONObject): Result<Project> = runCatching {
        val rawName = bundle.optString("name").ifBlank { "Imported" }
        val project = create(rawName).getOrElse {
            create("${sanitizeName(rawName)}_${System.currentTimeMillis() % 10000}").getOrThrow()
        }
        val files = bundle.optJSONObject("files") ?: JSONObject()
        files.keys().forEach { key ->
            val path = key as String
            if (".." in path) return@forEach
            writeFile(project, path, files.optString(path)).getOrThrow()
        }
        project
    }

    fun meta(project: Project): JSONObject {
        val file = File(project.directory, "project.json")
        return if (file.exists()) JSONObject(file.readText()) else JSONObject().put("name", project.name)
    }

    fun saveMeta(project: Project, json: JSONObject) {
        json.put("name", project.name)
        File(project.directory, "project.json").writeText(json.toString(2) + "\n")
    }

    fun seedDemo(): Project {
        find("SkyRunner")?.let { return it }
        val project = create("SkyRunner", "3d").getOrThrow()
        writeDemo(project.directory)
        return project
    }

    private fun writeStarter(dir: File, name: String, dimension: String) {
        val is3d = dimension == "3D"
        File(dir, "Scripts/Player.js").writeText(starterJs(name, is3d))
        File(dir, "Scripts/Player.cs").writeText(starterCs(name, is3d))
        val scene = GameScene(
            name = "Main",
            dimension = dimension,
            objects = starterObjects(is3d).toMutableList(),
            file = File(dir, "Scenes/Main.scene.json"),
        )
        SceneStore().save(scene)
        File(dir, "Assets/readme.txt").writeText(
            "Drop text assets here. Meshes are generated primitives (cube, sprite, ground).\n",
        )
    }

    private fun writeDemo(dir: File) {
        File(dir, "Scripts/Player.js").writeText(SKY_JS)
        File(dir, "Scripts/Player.cs").writeText(SKY_CS)
        File(dir, "Scripts/Coin.js").writeText(COIN_JS)
        File(dir, "Scripts/Hazard.js").writeText(HAZARD_JS)
        val objects = mutableListOf(
            SceneObject("MainCamera", "Camera", 0f, 6f, 12f, rx = -22f, color = "#9aa4b2", solid = false),
            SceneObject("Light", "Light", 4f, 10f, 2f, color = "#fff4cc", solid = false),
            SceneObject("Arena", "Ground", 0f, 0f, 0f, sx = 28f, sy = 1f, sz = 40f, color = "#2a3144"),
            SceneObject("Player", "Player", 0f, 1f, 10f, color = "#b69cff", script = "Scripts/Player.js", speed = 8f),
            SceneObject("Gate", "Mesh", 0f, 1.5f, -14f, sx = 8f, sy = 3f, sz = 1f, color = "#75e6da"),
            SceneObject("CoinA", "Coin", -3f, 1.2f, 4f, color = "#f4c95d", script = "Scripts/Coin.js", solid = false),
            SceneObject("CoinB", "Coin", 3f, 1.2f, 0f, color = "#f4c95d", script = "Scripts/Coin.js", solid = false),
            SceneObject("CoinC", "Coin", 0f, 1.2f, -6f, color = "#f4c95d", script = "Scripts/Coin.js", solid = false),
            SceneObject("SpikeL", "Enemy", -5f, 1f, 2f, color = "#ffb2c8", script = "Scripts/Hazard.js", speed = 2f),
            SceneObject("SpikeR", "Enemy", 5f, 1f, -4f, color = "#ffb2c8", script = "Scripts/Hazard.js", speed = 2f),
        )
        SceneStore().save(GameScene("Main", "3D", objects, File(dir, "Scenes/Main.scene.json")))
        File(dir, "README.md").writeText(
            """
            # SkyRunner

            Demo arena: collect coins, avoid spikes, reach the cyan gate.
            Controls: WASD / joystick, Space or Action to jump.
            """.trimIndent() + "\n",
        )
    }

    private fun starterObjects(is3d: Boolean): List<SceneObject> {
        return if (is3d) {
            listOf(
                SceneObject("MainCamera", "Camera", 0f, 5f, 10f, rx = -18f, color = "#9aa4b2", solid = false),
                SceneObject("Light", "Light", 3f, 8f, 2f, color = "#fff4cc", solid = false),
                SceneObject("Ground", "Ground", 0f, 0f, 0f, sx = 16f, sy = 1f, sz = 16f, color = "#2a3144"),
                SceneObject("Player", "Player", 0f, 1f, 4f, color = "#b69cff", script = "Scripts/Player.js", speed = 6f),
                SceneObject("Cube", "Mesh", 3f, 1f, -2f, color = "#75e6da"),
            )
        } else {
            listOf(
                SceneObject("MainCamera", "Camera", 0f, 0f, 10f, color = "#9aa4b2", solid = false),
                SceneObject("Ground", "Ground", 0f, -4f, 0f, sx = 24f, sy = 1f, sz = 1f, color = "#2a3144"),
                SceneObject("Player", "Player", -4f, -2.5f, 0f, color = "#b69cff", script = "Scripts/Player.js", speed = 7f),
                SceneObject("Platform", "Sprite", 3f, -1f, 0f, sx = 3f, sy = 0.6f, color = "#453b61"),
            )
        }
    }

    companion object {
        fun resolveRoot(context: Context): File {
            val externalBase = context.getExternalFilesDir(null) ?: context.filesDir
            val external = File(externalBase, "projects").apply { mkdirs() }
            val internal = File(context.filesDir, "projects")
            if (internal.exists()) {
                internal.listFiles()?.forEach { src ->
                    val dest = File(external, src.name)
                    if (!dest.exists()) runCatching { src.copyRecursively(dest) }
                }
            }
            return external
        }

        fun sanitizeName(raw: String): String =
            raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)

        private fun starterJs(name: String, is3d: Boolean): String = """
            // $name player — runs in MobileForge Play mode
            function onStart(api) {
              api.log("Player ready");
            }
            function onUpdate(api, dt) {
              const speed = api.object.speed || 6;
              api.move(api.input.x * speed * dt, 0, api.input.y * speed * dt);
              if (api.input.jump) api.jump(7);
            }
            function onCollisionEnter(api, other) {
              if (other.type === "Coin") {
                api.addScore(1);
                api.destroy(other.name);
              }
            }
        """.trimIndent() + "\n"

        private fun starterCs(name: String, is3d: Boolean): String = """
            // $name — C# component. Play mode also accepts the JS twin.
            public class Player {
                public float speed = 6f;

                void Start() {
                    // scene loaded
                }

                void Update() {
                    Move(input.horizontal * speed, input.vertical * speed);
                    if (input.jump) Jump(7f);
                }

                void OnCollisionEnter(other) {
                    if (other.type == "Coin") {
                        AddScore(1);
                        Destroy(other.name);
                    }
                }
            }
        """.trimIndent() + "\n"

        const val SKY_JS = """
function onStart(api) {
  api.object.speed = 8;
  api.log("SkyRunner online");
}
function onUpdate(api, dt) {
  const speed = api.object.speed || 8;
  api.move(api.input.x * speed * dt, 0, -api.input.y * speed * dt);
  if (api.input.jump) api.jump(8);
  if (api.object.z < -14) {
    api.addScore(10);
    api.setPosition(0, 1, 10);
    api.log("Gate reached");
  }
}
function onCollisionEnter(api, other) {
  if (other.type === "Coin") {
    api.addScore(5);
    api.destroy(other.name);
  }
  if (other.type === "Enemy") {
    api.addScore(-2);
    api.setPosition(0, 1, 10);
  }
}
"""

        const val SKY_CS = """
public class Player {
    public float speed = 8f;
    void Start() { }
    void Update() {
        Move(input.horizontal * speed, -input.vertical * speed);
        if (input.jump) Jump(8f);
    }
    void OnCollisionEnter(other) {
        if (other.type == "Coin") { AddScore(5); Destroy(other.name); }
        if (other.type == "Enemy") { AddScore(-2); }
    }
}
"""

        const val COIN_JS = """
function onStart(api) { api.object.solid = false; }
function onUpdate(api, dt) { api.object.ry = (api.object.ry || 0) + dt * 120; }
"""

        const val HAZARD_JS = """
function onUpdate(api, dt) {
  const t = api.time.elapsed;
  api.object.x = Math.sin(t * (api.object.speed || 2)) * 5;
}
"""
    }
}
