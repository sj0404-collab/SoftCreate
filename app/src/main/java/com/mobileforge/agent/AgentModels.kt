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
        val json = extractJson(cleaned) ?: return AgentAction(null, JSONObject(), true, cleaned.trim())
        val done = json.optBoolean("done") || json.optString("tool") == "done"
        val tool = if (json.isNull("tool")) null else json.optString("tool").takeIf { it.isNotBlank() && it != "null" }
        val args = json.optJSONObject("args") ?: JSONObject()
        json.keys().forEach { key ->
            if (key != "tool" && key != "args" && key != "done" && key != "say") {
                if (!args.has(key)) args.put(key, json.get(key))
            }
        }
        val say = listOf("say", "message").firstNotNullOfOrNull { key ->
            if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }.orEmpty()
        return AgentAction(tool, args, done, say)
    }

    data class View(val thinking: String, val say: String, val json: String)

    fun display(raw: String, streamedThinking: String = ""): View {
        var s = raw.replace(Regex("(?:null){2,}"), "")
        val think = StringBuilder()
        if (streamedThinking.isNotBlank()) {
            think.appendLine(streamedThinking.replace(Regex("(?:null){2,}"), "").trim())
        }
        val tag = Regex("(?is)<(?:think|thinking|reason|reasoning)>(.*?)</(?:think|thinking|reason|reasoning)>")
        tag.findAll(s).forEach { think.appendLine(it.groupValues[1].trim()) }
        s = tag.replace(s, " ")
        val startIdx = s.indexOf('{')
        val prose = if (startIdx < 0) s.trim() else s.take(startIdx).trim()
        if (prose.isNotBlank() && !prose.startsWith("```") && !prose.startsWith("{")) {
            think.appendLine(prose)
        }
        val json = if (startIdx >= 0) s.substring(startIdx) else ""
        val sayMatch = Regex("\"say\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL).find(json)
        var say = sayMatch?.groupValues?.get(1).orEmpty()
        val quote = say.indexOf('"')
        if (quote >= 0) say = say.take(quote)
        say = say.replace("\\n", "\n").replace("\\\"", "\"")
        return View(think.toString().trim(), say, json)
    }

    fun extractJson(raw: String): JSONObject? {
        val trimmed = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
        val candidate = fence?.groupValues?.get(1)?.trim() ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(candidate.substring(start, end + 1)) }.getOrNull()
    }

    const val SYSTEM = """
Ты агент MobileForge на телефоне. Человек — РЕЖИССЁР. Ты только выполняешь его заказ.
Думай и пиши ТОЛЬКО по-русски: и размышления, и say. Никакого английского в тексте для человека.
Ответ — ОДИН JSON-объект. Никогда слово null. Без markdown.

Инструмент: {"tool":"NAME","args":{...},"say":"кратко по-русски что делаешь"}
Финиш: {"done":true,"say":"итог по-русски: что создано"}

Инструменты:
- project.create {"name":"ИмяИзЗаказа","type":"3d"}
- project.list {}
- project.open {"name":"Имя"}
- project.path {}
- project.seed_demo {}
- fs.list {}
- fs.read {"path":"Scripts/Player.js"}
- fs.write {"path":"Scripts/Player.js","content":"..."}
- scene.create {"name":"Main","dimension":"3D"}
- scene.list {}
- scene.add_object {"type":"Ground","name":"Лес","color":"#1f5c3a","mesh":"Plane","sx":40,"sz":40,"x":-30}
- scene.add_object {"type":"Player","name":"Герой","color":"#e8c07a","mesh":"Capsule"}
- scene.add_object {"type":"Npc","name":"Страж","color":"#c48a6a","mesh":"Capsule","x":4}
- asset.create {"kind":"material","name":"Лес","color":"#1f5c3a","pattern":"noise","accent":"#0b2416","mesh":"Plane"}
- asset.create {"kind":"mesh","name":"Кристалл","mesh":"pyramid"}
- asset.create {"kind":"sound","name":"Шаг","freq":220}
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
- Скрипты: JS api.move / api.jump / api.input / api.addScore. Пиши Scripts/Player.js. Не C# UnityEngine — здесь это не работает.
- scene.list перед тем как плодить дубликаты. Не копируй Player/Ground без нужды.
- project.seed_demo только если просили демо SkyRunner.
- Не заканчивай done, пока заказ не выполнен. Пустая сцена после «сделай РПГ» — провал.
- После asset.create сразу вешай material/mesh на объекты: scene.add_object с material="Assets/Materials/Имя.mat" и mesh="Capsule|Plane|pyramid". Иначе ассет не виден.
- Звуки только asset.create kind=sound. Потом api.playSound("Имя") в скрипте.
- Для механик игры ОБЯЗАТЕЛЬНО plugin.create с рабочим JS в code (onPlay/onAddObject). Без плагина задумка не считается сделанной.
- В каждом вызове инструмента поле say — сначала по-русски ЧТО делаешь, потом инструмент. Не пиши JSON человеку.
"""
}
