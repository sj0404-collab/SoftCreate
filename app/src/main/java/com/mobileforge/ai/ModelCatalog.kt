package com.mobileforge.ai

data class AiModel(
    val providerId: String,
    val id: String,
    val label: String,
    val free: Boolean,
    val needKey: Boolean,
)

object ModelCatalog {
    val all: List<AiModel> = listOf(
        AiModel("zen", "auto", "Авто · бесплатные", free = true, needKey = false),
        AiModel("zen", "laguna-s-2.1-free", "Laguna S 2.1", free = true, needKey = false),
        AiModel("zen", "deepseek-v4-flash-free", "DeepSeek Flash", free = true, needKey = false),
        AiModel("zen", "big-pickle", "Big Pickle", free = true, needKey = false),
        AiModel("zen", "mimo-v2.5-free", "MiMo 2.5", free = true, needKey = false),
        AiModel("zen", "hy3-free", "Hy3", free = true, needKey = false),
        AiModel("zen", "nemotron-3.5-lightning-free", "Nemotron Lightning", free = true, needKey = false),
        AiModel("zen", "nemotron-3-ultra-free", "Nemotron Ultra", free = true, needKey = false),
        AiModel("zen", "deepseek-v4-flash", "DeepSeek Flash+", free = false, needKey = true),
        AiModel("zen", "deepseek-v4-pro", "DeepSeek Pro", free = false, needKey = true),
        AiModel("zen", "glm-5.2", "GLM 5.2", free = false, needKey = true),
        AiModel("zen", "kimi-k3", "Kimi K3", free = false, needKey = true),
        AiModel("zen", "kimi-k2.7-code", "Kimi K2.7 Code", free = false, needKey = true),
        AiModel("zen", "minimax-m3", "MiniMax M3", free = false, needKey = true),
        AiModel("zen", "claude-haiku-4-5", "Claude Haiku 4.5", free = false, needKey = true),
        AiModel("zen", "gpt-5.4-mini", "GPT 5.4 Mini", free = false, needKey = true),

        AiModel("openrouter", "openrouter/free", "OR Auto Free", free = true, needKey = true),
        AiModel("openrouter", "poolside/laguna-s-2.1:free", "OR Laguna S 2.1", free = true, needKey = true),
        AiModel("openrouter", "poolside/laguna-xs-2.1:free", "OR Laguna XS", free = true, needKey = true),
        AiModel("openrouter", "z-ai/glm-5.2:free", "OR GLM 5.2", free = true, needKey = true),
        AiModel("openrouter", "google/gemma-4-31b-it:free", "OR Gemma 4 31B", free = true, needKey = true),
        AiModel("openrouter", "google/gemma-4-26b-a4b-it:free", "OR Gemma 4 26B", free = true, needKey = true),
        AiModel("openrouter", "nvidia/nemotron-3.5-lightning:free", "OR Nemotron Light", free = true, needKey = true),
        AiModel("openrouter", "nvidia/nemotron-3-nano-30b-a3b:free", "OR Nemotron Nano", free = true, needKey = true),
        AiModel("openrouter", "nvidia/nemotron-3-super-120b-a12b:free", "OR Nemotron Super", free = true, needKey = true),
        AiModel("openrouter", "nvidia/nemotron-3-ultra-550b-a55b:free", "OR Nemotron Ultra", free = true, needKey = true),
        AiModel("openrouter", "openai/gpt-oss-20b:free", "OR GPT-OSS 20B", free = true, needKey = true),
        AiModel("openrouter", "cohere/north-mini-code:free", "OR North Mini Code", free = true, needKey = true),
        AiModel("openrouter", "openai/gpt-4o-mini", "OR GPT-4o Mini", free = false, needKey = true),
        AiModel("openrouter", "openai/gpt-5.4-mini", "OR GPT 5.4 Mini", free = false, needKey = true),
        AiModel("openrouter", "google/gemini-2.5-flash", "OR Gemini 2.5 Flash", free = false, needKey = true),
        AiModel("openrouter", "google/gemini-2.5-flash-lite", "OR Gemini 2.5 Lite", free = false, needKey = true),
        AiModel("openrouter", "anthropic/claude-haiku-4.5", "OR Claude Haiku 4.5", free = false, needKey = true),
        AiModel("openrouter", "anthropic/claude-sonnet-5", "OR Claude Sonnet 5", free = false, needKey = true),
        AiModel("openrouter", "deepseek/deepseek-chat-v3.1", "OR DeepSeek V3.1", free = false, needKey = true),
        AiModel("openrouter", "deepseek/deepseek-v4-flash", "OR DeepSeek V4 Flash", free = false, needKey = true),
        AiModel("openrouter", "qwen/qwen3-coder-flash", "OR Qwen3 Coder", free = false, needKey = true),
        AiModel("openrouter", "moonshotai/kimi-k3", "OR Kimi K3", free = false, needKey = true),
        AiModel("openrouter", "openrouter/auto", "OR Auto Paid", free = false, needKey = true),

        AiModel("orca", "orcarouter/free", "Orca Free", free = true, needKey = true),
        AiModel("orca", "deepseek/deepseek-v4-flash-free", "Orca DeepSeek Flash", free = true, needKey = true),
        AiModel("orca", "deepseek/deepseek-v4-pro-free", "Orca DeepSeek Pro", free = true, needKey = true),
        AiModel("orca", "tencent/hy3-free", "Orca Hy3", free = true, needKey = true),
        AiModel("orca", "deepseek/deepseek-v4-flash", "Orca DeepSeek+", free = false, needKey = true),
        AiModel("orca", "google/gemini-2.5-flash", "Orca Gemini 2.5", free = false, needKey = true),

        AiModel("gemini", "gemini-2.5-flash", "Gemini 2.5 Flash", free = true, needKey = true),
        AiModel("gemini", "gemini-2.0-flash", "Gemini 2.0 Flash", free = true, needKey = true),
        AiModel("gemini", "gemini-2.0-flash-lite", "Gemini 2.0 Lite", free = true, needKey = true),
        AiModel("gemini", "gemini-1.5-flash", "Gemini 1.5 Flash", free = true, needKey = true),
    )

    val groups: List<Pair<String, List<AiModel>>> = listOf(
        "Zen · бесплатно" to all.filter { it.providerId == "zen" && it.free },
        "Zen · платно (ключ Zen)" to all.filter { it.providerId == "zen" && !it.free },
        "OpenRouter · бесплатно" to all.filter { it.providerId == "openrouter" && it.free },
        "OpenRouter · платно" to all.filter { it.providerId == "openrouter" && !it.free },
        "Orca" to all.filter { it.providerId == "orca" },
        "Gemini" to all.filter { it.providerId == "gemini" },
    )

    fun pretty(id: String): String =
        all.firstOrNull { it.id == id }?.label
            ?: id.substringAfterLast('/').removeSuffix(":free").take(22)

    fun defaultId(providerId: String): String = when (providerId) {
        "openrouter" -> "openrouter/free"
        "orca" -> "orcarouter/free"
        "gemini" -> "gemini-2.5-flash"
        else -> "laguna-s-2.1-free"
    }

    fun idsFor(providerId: String): List<String> =
        all.filter { it.providerId == providerId && it.id != "auto" }.map { it.id }

    fun idOf(provider: Provider): String = when (provider) {
        Provider.ZEN_DIRECT -> "zen"
        Provider.OPENROUTER -> "openrouter"
        Provider.ORCA -> "orca"
        Provider.GEMINI -> "gemini"
        Provider.CUSTOM -> "custom"
        Provider.LOCAL_MCP -> "mcp"
    }

    fun providerOf(id: String): Provider? = when (all.firstOrNull { it.id == id }?.providerId) {
        "zen" -> Provider.ZEN_DIRECT
        "openrouter" -> Provider.OPENROUTER
        "orca" -> Provider.ORCA
        "gemini" -> Provider.GEMINI
        else -> null
    }

    fun remap(provider: Provider, model: String): String {
        if (model.isBlank() || model == "auto") return defaultFor(provider)
        val natives = all.filter { toProvider(it.providerId) == provider && it.id != "auto" }
        if (natives.any { it.id == model }) return model
        val needle = when {
            "laguna" in model.lowercase() -> "laguna"
            "big-pickle" in model.lowercase() || "pickle" in model.lowercase() -> "pickle"
            "deepseek" in model.lowercase() -> "deepseek"
            "mimo" in model.lowercase() -> "mimo"
            "hy3" in model.lowercase() -> "hy3"
            "nemotron" in model.lowercase() && "ultra" in model.lowercase() -> "ultra"
            "nemotron" in model.lowercase() -> "nemotron"
            "gemma" in model.lowercase() -> "gemma"
            "gemini" in model.lowercase() -> "gemini"
            "glm" in model.lowercase() -> "glm"
            "kimi" in model.lowercase() -> "kimi"
            "qwen" in model.lowercase() -> "qwen"
            "claude" in model.lowercase() -> "claude"
            "gpt" in model.lowercase() -> "gpt"
            else -> null
        }
        if (needle != null) {
            natives.firstOrNull { needle in it.id.lowercase() && it.free }?.let { return it.id }
            natives.firstOrNull { needle in it.id.lowercase() }?.let { return it.id }
        }
        return natives.firstOrNull { it.free }?.id ?: natives.firstOrNull()?.id ?: defaultFor(provider)
    }

    fun plan(
        preferred: Provider,
        model: String,
        hasKey: (Provider) -> Boolean,
    ): List<Pair<Provider, String>> {
        val out = LinkedHashSet<Pair<Provider, String>>()
        fun add(provider: Provider, id: String) {
            if (id.isBlank() || id == "auto") return
            if (provider != Provider.ZEN_DIRECT && !hasKey(provider)) return
            val meta = all.firstOrNull { it.id == id && toProvider(it.providerId) == provider }
            if (meta?.needKey == true && !hasKey(provider)) return
            out.add(provider to id)
        }

        if (model != "auto" && model.isNotBlank()) {
            add(preferred, model)
            return out.toList()
        }
        fun addFree(provider: Provider, limit: Int) {
            all.filter { toProvider(it.providerId) == provider && it.free && it.id != "auto" }
                .take(limit)
                .forEach { add(provider, it.id) }
        }
        addFree(preferred, 5)
        return out.toList().take(6)
    }

    fun zenFreeIds(): List<String> =
        all.filter { it.providerId == "zen" && it.free && it.id != "auto" }.map { it.id }

    private fun defaultFor(provider: Provider): String = when (provider) {
        Provider.OPENROUTER -> "openrouter/free"
        Provider.ORCA -> "orcarouter/free"
        Provider.GEMINI -> "gemini-2.5-flash"
        else -> "laguna-s-2.1-free"
    }

    private fun toProvider(id: String): Provider? = when (id) {
        "zen" -> Provider.ZEN_DIRECT
        "openrouter" -> Provider.OPENROUTER
        "orca" -> Provider.ORCA
        "gemini" -> Provider.GEMINI
        else -> null
    }
}
