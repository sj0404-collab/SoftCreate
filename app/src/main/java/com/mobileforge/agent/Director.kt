package com.mobileforge.agent

object Director {
    private val bannedDefaults = setOf(
        "skyarena", "new2dgame", "new3dgame", "demo", "testgame", "game", "проект", "игра",
    )

    private val controlHints = listOf(
        "джойстик", "джойст", "управлен", "кнопк", "стик", "joystick",
        "touch", "hud", "прыж", "jump", "атак", "attack", "стрель",
    )

    private val animHints = listOf(
        "анимац", "animation", "animate", "tween",
    )

    fun extractProjectName(task: String): String? {
        Regex("""[«"']([\p{L}\p{N}_-]{2,40})[»"']""").find(task)?.groupValues?.get(1)?.let { hit ->
            if (hit.lowercase() !in bannedDefaults) return hit
        }
        Regex(
            """(?i)(?:назван[иея]|назови|называется|проект|игру|игра|game|project|called|named)\s+[«"']?([\p{L}\p{N}_-]{2,40})""",
        ).find(task)?.groupValues?.get(1)?.let { hit ->
            if (hit.lowercase() !in setOf("игру", "игра", "проект", "game", "project", "назови", "новая", "новый") &&
                hit.lowercase() !in bannedDefaults
            ) {
                return hit
            }
        }
        return null
    }

    fun inventName(task: String): String {
        val stop = bannedDefaults + setOf(
            "придумай", "придумать", "создай", "создать", "сделай", "сделать",
            "игру", "новая", "новый", "please", "make", "create",
        )
        val words = task.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it.lowercase() !in stop }
        val pick = words.firstOrNull { it[0].isUpperCase() } ?: words.lastOrNull() ?: "Game"
        val tag = (task.hashCode().toUInt().toString(16)).take(4)
        return "${pick.take(24)}_$tag"
    }

    fun resolveName(task: String, proposed: String): String {
        val extracted = extractProjectName(task)
        if (extracted != null) return extracted
        if (proposed.isNotBlank() && proposed.lowercase() !in bannedDefaults) return proposed
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
}
