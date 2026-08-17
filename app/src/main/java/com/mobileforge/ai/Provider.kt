package com.mobileforge.ai

enum class Provider {
    ZEN_DIRECT,
    OPENROUTER,
    LOCAL_MCP,
    CUSTOM,
    ;

    companion object {
        fun fromId(id: String): Provider = when (id.lowercase()) {
            "zen", "zen_direct", "zendirect" -> ZEN_DIRECT
            "openrouter" -> OPENROUTER
            "mcp", "local_mcp", "localmcp" -> LOCAL_MCP
            "custom" -> CUSTOM
            else -> error("Unsupported provider: $id")
        }
    }
}
