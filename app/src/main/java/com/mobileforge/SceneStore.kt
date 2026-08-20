package com.mobileforge

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class SceneStore {
    fun scenes(project: ProjectStore.Project): List<GameScene> {
        val dir = File(project.directory, "Scenes").apply { mkdirs() }
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".scene.json") }
            ?.mapNotNull { load(it) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun create(project: ProjectStore.Project, rawName: String, dimension: String): Result<GameScene> =
        runCatching {
            val name = ProjectStore.sanitizeName(rawName)
            require(name.isNotBlank()) { "Enter a scene name." }
            val dim = dimension.uppercase()
            require(dim == "2D" || dim == "3D") { "Invalid scene type." }
            val file = File(project.directory, "Scenes/$name.scene.json")
            require(!file.exists()) { "Scene already exists." }
            val objects = CopyOnWriteArrayList<SceneObject>()
            if (dim == "3D") {
                objects += SceneObject("MainCamera", "Camera", 0f, 5f, 10f, rx = -18f, color = "#9aa4b2", solid = false)
                objects += SceneObject("Light", "Light", 3f, 8f, 2f, color = "#fff4cc", solid = false)
            } else {
                objects += SceneObject("MainCamera", "Camera", 0f, 0f, 10f, color = "#9aa4b2", solid = false)
            }
            val scene = GameScene(name, dim, objects, file)
            save(scene)
            scene
        }

    fun save(scene: GameScene) {
        scene.file.parentFile?.mkdirs()
        scene.file.writeText(scene.toJson().toString(2) + "\n")
    }

    fun saveJson(project: ProjectStore.Project, json: JSONObject): Result<GameScene> = runCatching {
        val name = ProjectStore.sanitizeName(json.optString("name"))
        require(name.isNotBlank()) { "Scene name is required." }
        val file = File(project.directory, "Scenes/$name.scene.json")
        val objects = CopyOnWriteArrayList<SceneObject>()
        val items = json.optJSONArray("objects") ?: JSONArray()
        for (i in 0 until items.length()) {
            objects.add(SceneObject.fromJson(items.getJSONObject(i)))
        }
        val scene = GameScene(
            name = name,
            dimension = json.optString("dimension", "3D"),
            objects = CopyOnWriteArrayList(objects),
            file = file,
        )
        save(scene)
        scene
    }

    fun load(file: File): GameScene? = try {
        val data = JSONObject(file.readText())
        val objects = CopyOnWriteArrayList<SceneObject>()
        val values = data.optJSONArray("objects") ?: JSONArray()
        for (i in 0 until values.length()) {
            objects.add(SceneObject.fromJson(values.getJSONObject(i)))
        }
        GameScene(
            name = data.optString("name", file.name.removeSuffix(".scene.json")),
            dimension = data.optString("dimension", "2D"),
            objects = CopyOnWriteArrayList(objects),
            file = file,
        )
    } catch (_: Exception) {
        null
    }

    fun delete(project: ProjectStore.Project, name: String): Result<Unit> = runCatching {
        val file = File(project.directory, "Scenes/${ProjectStore.sanitizeName(name)}.scene.json")
        require(file.exists()) { "Scene not found." }
        require(file.delete()) { "Delete failed." }
    }
}
