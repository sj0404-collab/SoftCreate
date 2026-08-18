package com.mobileforge.engine

data class Suggestion(
    val label: String,
    val insert: String,
    val detail: String,
    val kind: String,
)

object Completions {
    private val api = listOf(
        Suggestion("onStart", "onStart(api) {\n  \n}", "хук старта сцены", "fn"),
        Suggestion("onUpdate", "onUpdate(api, dt) {\n  \n}", "хук каждого кадра", "fn"),
        Suggestion("onCollisionEnter", "onCollisionEnter(api, other) {\n  \n}", "хук столкновения", "fn"),
        Suggestion("onButtonClick", "onButtonClick(api) {\n  \n}", "хук Action", "fn"),
        Suggestion("onSceneLoaded", "onSceneLoaded(api) {\n  \n}", "хук загрузки", "fn"),
        Suggestion("api.move", "api.move(dx, dy, dz)", "сдвинуть объект", "api"),
        Suggestion("api.jump", "api.jump(force)", "прыжок, если на земле", "api"),
        Suggestion("api.setPosition", "api.setPosition(x, y, z)", "телепорт", "api"),
        Suggestion("api.addScore", "api.addScore(n)", "изменить счёт", "api"),
        Suggestion("api.destroy", "api.destroy(name)", "удалить объект", "api"),
        Suggestion("api.find", "api.find(name)", "найти объект сцены", "api"),
        Suggestion("api.spawn", "api.spawn({name, type, x, y, z})", "создать объект", "api"),
        Suggestion("api.log", "api.log(message)", "сообщение в HUD", "api"),
        Suggestion("api.loadScene", "api.loadScene(name)", "переключить сцену", "api"),
        Suggestion("api.input.x", "api.input.x", "ось A/D / стик X", "api"),
        Suggestion("api.input.y", "api.input.y", "ось W/S / стик Y", "api"),
        Suggestion("api.input.jump", "api.input.jump", "пробел / Jump", "api"),
        Suggestion("api.input.action", "api.input.action", "Action", "api"),
        Suggestion("api.object.x", "api.object.x", "позиция X", "prop"),
        Suggestion("api.object.y", "api.object.y", "позиция Y", "prop"),
        Suggestion("api.object.z", "api.object.z", "позиция Z", "prop"),
        Suggestion("api.object.speed", "api.object.speed", "скорость", "prop"),
        Suggestion("api.object.color", "api.object.color", "цвет", "prop"),
        Suggestion("api.object.solid", "api.object.solid", "коллизии", "prop"),
        Suggestion("api.object.type", "api.object.type", "тип объекта", "prop"),
        Suggestion("api.object.name", "api.object.name", "имя объекта", "prop"),
        Suggestion("api.time.dt", "api.time.dt", "дельта кадра", "prop"),
        Suggestion("api.time.elapsed", "api.time.elapsed", "время сцены", "prop"),
        Suggestion("api.score", "api.score", "текущий счёт", "prop"),
        Suggestion("Math.sin", "Math.sin(x)", "синус", "math"),
        Suggestion("Math.cos", "Math.cos(x)", "косинус", "math"),
        Suggestion("Math.abs", "Math.abs(x)", "модуль", "math"),
        Suggestion("function", "function name() {\n  \n}", "объявление функции", "kw"),
        Suggestion("if", "if (cond) {\n  \n}", "условие", "kw"),
        Suggestion("const", "const name = ", "константа", "kw"),
        Suggestion("let", "let name = ", "переменная", "kw"),
        Suggestion("return", "return ", "возврат", "kw"),
    )

    private val csharp = listOf(
        Suggestion("void Update", "void Update() {\n    Move(input.horizontal * speed, input.vertical * speed);\n}", "кадр", "fn"),
        Suggestion("void Start", "void Start() {\n    \n}", "старт", "fn"),
        Suggestion("void OnCollisionEnter", "void OnCollisionEnter(other) {\n    \n}", "коллизия", "fn"),
        Suggestion("void OnTriggerEnter", "void OnTriggerEnter(other) {\n    \n}", "триггер", "fn"),
        Suggestion("void LateUpdate", "void LateUpdate() {\n    \n}", "после кадра", "fn"),
        Suggestion("Move", "Move(x, z)", "движение", "api"),
        Suggestion("Jump", "Jump(7f)", "прыжок", "api"),
        Suggestion("AddScore", "AddScore(1)", "счёт", "api"),
        Suggestion("Destroy", "Destroy(other.name)", "удалить", "api"),
        Suggestion("Instantiate", "Instantiate(prefab, x, y, z)", "спавн", "api"),
        Suggestion("Find", "Find(\"Name\")", "поиск", "api"),
        Suggestion("transform.position", "transform.position", "позиция", "api"),
        Suggestion("transform.Rotate", "transform.Rotate(0f, 90f * dt, 0f)", "поворот", "api"),
        Suggestion("Camera.main", "Camera.main", "главная камера", "api"),
        Suggestion("Light.intensity", "Light.intensity", "свет", "api"),
        Suggestion("input.horizontal", "input.horizontal", "ось X", "api"),
        Suggestion("input.vertical", "input.vertical", "ось Z/Y", "api"),
        Suggestion("input.jump", "input.jump", "прыжок", "api"),
        Suggestion("Time.deltaTime", "Time.deltaTime", "dt", "api"),
        Suggestion("Rigidbody", "public float mass = 1f;", "физика", "kw"),
        Suggestion("SerializeField", "[SerializeField] float speed = 6f;", "инспектор", "kw"),
        Suggestion("public class", "public class Component : ForgeBehaviour {\n    public float speed = 6f;\n    void Update() {\n        \n    }\n}", "компонент", "kw"),
        Suggestion("public float", "public float speed = 1f;", "поле", "kw"),
        Suggestion("using MobileForge", "using MobileForge;\n", "namespace", "kw"),
    )

    fun suggest(prefix: String, path: String?, extra: List<String> = emptyList()): List<Suggestion> {
        val p = prefix.trim()
        if (p.isBlank()) return emptyList()
        val pool = buildList {
            addAll(if (path?.endsWith(".cs", true) == true) csharp else api)
            extra.forEach { add(Suggestion(it, it, "символ проекта", "sym")) }
        }
        return pool
            .filter { it.label.contains(p, true) || it.insert.startsWith(p) }
            .distinctBy { it.label }
            .take(12)
    }

    fun prefixAt(text: String, cursor: Int): Pair<Int, String> {
        val i = cursor.coerceIn(0, text.length)
        var s = i
        while (s > 0) {
            val c = text[s - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '$') s-- else break
        }
        return s to text.substring(s, i)
    }
}
