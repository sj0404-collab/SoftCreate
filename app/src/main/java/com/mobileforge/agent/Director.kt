package com.mobileforge.agent

object Director {
    private val bannedDefaults = setOf(
        "skyarena", "new2dgame", "new3dgame", "demo", "testgame", "game", "проект", "игра",
    )

    private val nameStop = setOf(
        "игру", "игра", "проект", "game", "project", "назови", "новая", "новый", "новое",
        "для", "или", "либо", "сам", "также", "без", "чтобы", "как", "что", "это", "этот",
        "эта", "the", "with", "from", "and", "or", "for", "onto", "into", "onto", "a", "an",
        "please", "make", "create", "назван", "называется", "called", "named",
        "ассетов", "ассеты", "ассет", "assets", "asset", "качеств", "качестве", "хорошем",
        "pollination", "любую", "любой", "любое", "любая", "какую", "какой", "какое",
        "какая", "просто", "плагин", "плагины", "плагинов", "2d", "3d", "two", "three",
        "её", "ее", "ней", "неё",
    )

    private val inventStop = bannedDefaults + nameStop + setOf(
        "придумай", "придумать", "создай", "создать", "сделай", "сделать",
        "пожалуйста", "просто", "очень", "надо", "нужно", "хочу", "пусть",
        "будет", "должна", "должен", "можно", "там", "тут", "ещё", "еще",
        "любую", "любой", "плагины", "плагин", "неё", "нее",
    )

    private val controlHints = listOf(
        "джойстик", "джойст", "управлен", "кнопк", "стик", "joystick",
        "touch", "hud", "прыж", "jump", "атак", "attack", "стрель",
    )

    private val animHints = listOf(
        "анимац", "animation", "animate", "tween",
    )

    private val worldHints = listOf(
        "рпг", "rpg", "биом", "мир", "npc", "нпс", "населен", "квест", "open world",
        "открыт", "деревн", "город", "данж", "локац", "level", "уровен",
    )

    private val followExact = setOf(
        "ну", "ну как", "нукак", "ок", "ok", "да", "нет", "дальше", "продолжай", "продолжи",
        "ещё", "еще", "повтори", "повтор", "снова", "again", "next", "go", "go on",
        "что создал", "что сделал", "что ты создал", "что ты сделал", "ну давай", "ну что",
        "и", "ага", "угу", "хорошо", "ладно", "понял", "понятно", "ждём", "ждем",
        "почему пусто", "почему пустая", "пусто", "пустая", "сцена пуста",
    )

    fun extractProjectName(task: String): String? {
        Regex("""[«"']([\p{L}\p{N}_-]{2,40})[»"']""").find(task)?.groupValues?.get(1)?.let { hit ->
            if (hit.lowercase() !in bannedDefaults && hit.lowercase() !in nameStop) return hit
        }
        Regex(
            """(?i)(?:назван[иея]|назови|называется|проект\s+под\s+именем|called|named)\s+[«"']?([\p{L}\p{N}_-]{2,40})""",
        ).find(task)?.groupValues?.get(1)?.let { hit ->
            if (hit.lowercase() !in nameStop && hit.lowercase() !in bannedDefaults) return hit
        }
        Regex(
            """(?i)(?:игру|игра|game|project)\s+[«"']?([A-ZА-ЯЁ][\p{L}\p{N}_-]{1,39})""",
        ).find(task)?.groupValues?.get(1)?.let { hit ->
            if (hit.lowercase() !in nameStop && hit.lowercase() !in bannedDefaults) return hit
        }
        return null
    }

    fun inventName(task: String): String {
        val words = task.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it.lowercase() !in inventStop }
        val pick = words.firstOrNull { it[0].isUpperCase() } ?: words.firstOrNull() ?: "Game"
        val tag = (task.hashCode().toUInt().toString(16)).take(4)
        return "${pick.take(24)}_$tag"
    }

    fun resolveName(task: String, proposed: String, current: String? = null): String {
        if (isFollowUp(task) && !current.isNullOrBlank()) return current
        val extracted = extractProjectName(task)
        if (extracted != null) return extracted
        if (proposed.isNotBlank() &&
            proposed.lowercase() !in bannedDefaults &&
            proposed.lowercase() !in nameStop
        ) {
            return proposed
        }
        if (!current.isNullOrBlank() && !isBannedDefault(current)) return current
        return inventName(task)
    }

    fun isBannedDefault(name: String): Boolean = name.lowercase() in bannedDefaults

    fun wantsControls(task: String): Boolean {
        val low = task.lowercase()
        return controlHints.any { it in low }
    }

    fun wantsAnimation(task: String): Boolean {
        val low = task.lowercase()
        return animHints.any { it in low }
    }

    fun wantsWorld(task: String): Boolean {
        val low = task.lowercase()
        return worldHints.any { it in low }
    }

    fun wants2D(task: String): Boolean {
        val low = task.lowercase().replace(" ", "")
        if (Regex("(?<![a-zа-я0-9])3d(?![a-zа-я0-9])").containsMatchIn(task.lowercase())) {
            if (!task.lowercase().contains("2d") && "двумер" !in task.lowercase()) return false
        }
        val src = task.lowercase()
        return "2d" in src || "2-d" in src || "двумер" in src || "платформер" in src
    }

    fun isFollowUp(task: String): Boolean {
        val t = task.trim().lowercase().replace(Regex("[.!?…]+$"), "").trim()
        if (t.isEmpty()) return true
        if (t in followExact) return true
        if (t.length <= 3) return true
        if (t.matches(Regex("ну+"))) return true
        if (t.matches(Regex("^(что|где|зачем|почему|сколько|как дела|кто)\\b.*"))) return true
        return false
    }

    fun formatMs(ms: Long): String = when {
        ms < 0L -> "0мс"
        ms < 1000L -> "${ms}мс"
        ms < 10_000L -> {
            val s = ms / 100 / 10.0
            "${s}с"
        }
        else -> "${ms / 1000}с"
    }
}
