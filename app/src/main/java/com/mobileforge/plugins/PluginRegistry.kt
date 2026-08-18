package com.mobileforge.plugins

data class PluginInfo(
    val id: String,
    val title: String,
    val summary: String,
    val enabled: Boolean,
)

object PluginRegistry {
    val bundled = listOf(
        PluginInfo("primitives", "Primitive Factory", "Куб, сфера, цилиндр, капсула, плоскость, клин", true),
        PluginInfo("blocks", "Block Kit", "Воксельные блоки и палитра", true),
        PluginInfo("lighting", "Lighting Kit", "Directional / Point / Spot + интенсивность", true),
        PluginInfo("camera", "Cinemachine Lite", "FOV, clip planes, follow-preview", true),
        PluginInfo("github", "GitHub Cloud Build", "PAT, репо, workflow на runner", true),
        PluginInfo("mcp", "MCP Workbench", "Локальные и удалённые MCP tools", true),
        PluginInfo("csharp", "C# Scripting", ".cs компоненты и автодополнение", true),
        PluginInfo("assets", "Asset Database", "Materials, Models, Prefabs, Pack", true),
    )
}
