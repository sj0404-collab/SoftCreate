package com.mobileforge.agent

import org.json.JSONObject

sealed class AgentEvent {
    abstract val id: Long
    data class User(override val id: Long, val text: String) : AgentEvent()
    data class Assistant(
        override val id: Long,
        val text: String,
        val thinking: String = "",
        val live: Boolean = false,
        val ms: Long = 0,
        val raw: String = "",
    ) : AgentEvent()
    data class Tool(
        override val id: Long,
        val title: String,
        val tool: String,
        val args: String,
        val result: String,
        val ms: Long,
        val ok: Boolean,
        val expanded: Boolean = false,
    ) : AgentEvent()
}

data class AgentCard(
    val id: Long,
    val title: String,
    val tool: String,
    val args: String,
    val result: String,
    val ms: Long,
    val ok: Boolean,
    val expanded: Boolean = false,
)

data class AgentAction(
    val tool: String?,
    val args: JSONObject,
    val done: Boolean,
    val say: String,
)

object AgentParser {
    fun parse(raw: String): AgentAction {
        val cleaned = raw.replace(Regex("(?:null){2,}"), "")
        val xml = parseXmlTool(cleaned)
        val json = extractJson(cleaned)
        if (json != null) {
            val done = json.optBoolean("done") || json.optString("tool") == "done"
            val tool = if (json.isNull("tool")) null else json.optString("tool").takeIf { it.isNotBlank() && it != "null" && it != "done" }
            val args = json.optJSONObject("args") ?: JSONObject()
            json.keys().forEach { key ->
                if (key != "tool" && key != "args" && key != "done" && key != "say" && key != "message") {
                    if (!args.has(key)) args.put(key, json.get(key))
                }
            }
            val say = listOf("say", "message").firstNotNullOfOrNull { key ->
                if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() && it != "null" }
            }.orEmpty().ifBlank { xml?.say.orEmpty() }.ifBlank { humanText(cleaned) }
            if (tool != null) return AgentAction(tool, args, false, say)
            if (done) return AgentAction(null, args, true, say)
        }
        if (xml != null) return xml
        val say = humanText(cleaned)
        val attempt = looksLikeTool(cleaned)
        return AgentAction(null, JSONObject(), done = !attempt, say = say)
    }

    fun looksLikeTool(raw: String): Boolean {
        val s = raw.lowercase()
        return "<tool_call" in s || "</tool_call>" in s || "\"tool\"" in s ||
            Regex("""\b(project|scene|fs|asset|plugin|play|controls)\.\w+""").containsMatchIn(s)
    }

    fun parseXmlTool(raw: String): AgentAction? {
        val block = Regex("(?is)<tool_call>(.*?)</tool_call>").find(raw) ?: return null
        val inner = block.groupValues[1]
        val args = JSONObject()
        Regex("(?is)<arg_key>\\s*(.*?)\\s*</arg_key>\\s*<arg_value>\\s*(.*?)\\s*</arg_value>")
            .findAll(inner)
            .forEach { m ->
                val k = m.groupValues[1].trim()
                val v = m.groupValues[2].trim()
                if (k.isNotBlank()) args.put(k, v)
            }
        val name = inner.replace(Regex("(?is)<arg_key>[\\s\\S]*"), "").replace(Regex("<[^>]+>"), "").trim()
        if (name.isBlank() || ' ' in name) {
            val fallback = Regex("(?is)(project|scene|fs|asset|plugin|play|controls)\\.[a-z_.]+").find(inner)?.value
            if (fallback.isNullOrBlank()) return null
            val say = humanText(raw)
            return AgentAction(fallback, args, false, say)
        }
        return AgentAction(name, args, false, humanText(raw))
    }

    fun humanText(raw: String): String {
        var s = raw.replace(Regex("(?:null){2,}"), "")
        s = Regex("(?is)<tool_call>[\\s\\S]*?</tool_call>").replace(s, " ")
        s = Regex("(?is)</?tool_call>").replace(s, " ")
        s = Regex("(?is)<arg_(?:key|value)>[\\s\\S]*?</arg_(?:key|value)>").replace(s, " ")
        s = Regex("(?is)<(?:think|thinking|reason|reasoning)>[\\s\\S]*?</(?:think|thinking|reason|reasoning)>").replace(s, " ")
        s = stripJsonObjects(s)
        s = s.replace(Regex("(?im)^\\s*Let\\s*$"), " ")
        s = s.replace("```json", " ").replace("```", " ")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n").trim()
        return s.take(800)
    }

    private fun stripJsonObjects(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '{') {
                val end = matchBrace(raw, i)
                if (end > i) {
                    i = end + 1
                    continue
                }
            }
            sb.append(raw[i])
            i++
        }
        return sb.toString()
    }

    data class View(val thinking: String, val say: String, val json: String)

    fun display(raw: String, streamedThinking: String = ""): View {
        val action = parse(raw)
        var think = humanText(streamedThinking)
        val fromRaw = humanText(raw)
        if (think.isBlank()) think = fromRaw
        val say = action.say.ifBlank { fromRaw }
        val json = extractJson(raw)?.toString() ?: parseXmlTool(raw)?.let { "{\"tool\":\"${it.tool}\"}" }.orEmpty()
        val cleanThink = think.replace(say, "").trim().ifBlank { think }
        val thinking = if (cleanThink == say) "" else cleanThink
        return View(thinking, say, json)
    }

    fun extractJson(raw: String): JSONObject? {
        val trimmed = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
        val candidate = fence?.groupValues?.get(1)?.trim() ?: trimmed
        var i = 0
        while (i < candidate.length) {
            val start = candidate.indexOf('{', i)
            if (start < 0) return null
            val end = matchBrace(candidate, start)
            if (end > start) {
                val obj = runCatching { JSONObject(candidate.substring(start, end + 1)) }.getOrNull()
                if (obj != null && (obj.has("tool") || obj.has("done") || obj.has("say") || obj.has("args"))) {
                    return obj
                }
            }
            i = start + 1
        }
        return null
    }

    private fun matchBrace(text: String, open: Int): Int {
        var depth = 0
        var i = open
        var inStr = false
        var escape = false
        while (i < text.length) {
            val c = text[i]
            when {
                inStr && escape -> escape = false
                inStr && c == '\\' -> escape = true
                c == '"' -> {
                    inStr = !inStr
                    escape = false
                }
                !inStr && c == '{' -> depth++
                !inStr && c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
                else -> escape = false
            }
            i++
        }
        return -1
    }

    const val SYSTEM = """
Ты агент MobileForge на телефоне. Человек — РЕЖИССЁР. Ты только выполняешь его заказ.
Думай и пиши ТОЛЬКО по-русски: и размышления, и say. Никакого английского в тексте для человека.
Ответ — ОДИН JSON-объект. Никогда слово null. Без markdown. ЗАПРЕЩЕНО XML: <tool_call>, arg_key, arg_value. Запрещено несколько JSON подряд и слово Let.

Инструмент: {"tool":"NAME","args":{...},"say":"кратко по-русски что делаешь"}
Финиш: {"done":true,"say":"итог по-русски: что создано"}

Инструменты:
- project.create {"name":"ИмяИзЗаказа","type":"3d"}
- project.list {}
- project.open {"name":"Имя"}
- project.path {}
- project.seed_demo {}
- fs.list {}
- fs.read {"path":"Scripts/Player.cs"}
- fs.write {"path":"Scripts/Player.cs","content":"..."}
- github.whoami {}
- github.repos {}
- github.bind {"repo":"owner/name"}
- github.ls {"path":""}
- github.read {"path":"README.md"}
- github.write {"path":"file.cs","content":"...","message":"fix"}
- component.add {"name":"Герой","type":"Rigidbody"}
- prefab.save {"name":"Герой"}
- prefab.spawn {"name":"Герой","x":2}
- scene.create {"name":"Main","dimension":"3D"}
- scene.list {}
- scene.add_object {"type":"Ground","name":"Лес","color":"#1f5c3a","mesh":"Plane","sx":40,"sz":40,"x":-30}
- scene.add_object {"type":"Player","name":"Герой","color":"#e8c07a","mesh":"Capsule"}
- scene.add_object {"type":"Npc","name":"Страж","color":"#c48a6a","mesh":"Capsule","x":4}
- asset.create {"kind":"material","name":"Лес","color":"#1f5c3a","pattern":"noise","accent":"#0b2416","mesh":"Plane"}
- asset.create {"kind":"mesh","name":"Кристалл","mesh":"pyramid"}
- asset.create {"kind":"sound","name":"Шаг","freq":220}
- asset.create {"kind":"blender","name":"Скала","shape":"cube","color":"#6b7c5a"}
- blender.generate {"name":"Дерево","shape":"cylinder","color":"#2d5a27"}
- controls.set {"items":[...]}   ТОЛЬКО если режиссёр просил тач/джойстик/кнопки
- play.start {}
- plugin.create {"id":"rpgworld","title":"RPG World","code":"function onPlay(api){ api.log(\"play\"); }"}
- plugin.list {}
- plugin.run {"id":"scorepad","menu":"ping"}

Память (обязательно):
- Тебе дают всю переписку сессии. Короткое «ну» / «дальше» / «что создал» — это НЕ новый проект, а продолжение прошлого заказа. Не выдумывай новое имя.
- Не создавай пустышку из камеры и света. Если просили игру / РПГ / мир / биомы / NPC / население — СОБЕРИ МИР: земля, зоны, персонажи, уникальные ассеты, скрипты.
- Имя проекта — только если режиссёр явно назвал в кавычках или «назови». Не выдирай предлоги («для», «или»).
- Не забывай исходный заказ, пока не дали новый большой заказ.

Мир:
- Видимый мир = объекты сцены. Не используй фиолетовые кубы StudioPack как внешний вид.
- Для ЭТОЙ игры создай свои материалы/меши/звуки через asset.create, повесь их на объекты.
- Люди и NPC — Capsule, не Cube. Земля и биомы — Plane разных цветов. Пропсы — pyramid/crystal/wedge. «Без квадратов» = не ставь Cube.
- Если просили просторный мир и N биомов — N больших Plane со сдвигом по X/Z, разные цвета/имена, плюс жители.
- Анимации — только если просили (слово «анимации» в заказе = просили: сделай через скрипт api, без твинов движка если нет инструмента).
- Джойстик/HUD — только если просили.
- Play-скрипты сцены: Scripts/*.cs (ForgeBehaviour) или ноды. Остальные языки ПИШИ куда нужно: App/*.tsx, Android/*.kt|java, Android/smali/*.smali, Config/*.yml, Blender/*.py, Native/*.cpp, Web/*.html. Не клади TSX/Java в Scripts/ — агент перенесёт в свою папку.
- Blender в приложении (вкладка Blender): mesh.add / blender.generate {name,shape,color} — сетка в редакторе, OBJ в Assets/Meshes. mesh.save, mesh.apply на объект сцены. Десктопный .py по желанию.
- Делай всё, что нужно заказу: код, меши, графы, github, экспорт. Не выдумывай вредоносный smali/эксплойты.
- scene.list перед тем как плодить дубликаты. Не копируй Player/Ground без нужды.
- project.seed_demo только если просили демо SkyRunner.
- Не заканчивай done, пока заказ не выполнен. Пустая сцена после «сделай РПГ» — провал.
- После asset.create сразу вешай material/mesh на объекты: scene.add_object с material="Assets/Materials/Имя.mat" и mesh="Capsule|Plane|pyramid". Иначе ассет не виден.
- Звуки только asset.create kind=sound. Потом api.playSound("Имя") в скрипте.
- Для механик игры ОБЯЗАТЕЛЬНО plugin.create с рабочим JS в code (onPlay/onAddObject). Без плагина задумка не считается сделанной.
- В каждом вызове инструмента поле say — сначала по-русски ЧТО делаешь, потом инструмент. Не пиши JSON человеку.
"""
}
