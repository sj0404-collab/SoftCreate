package com.mobileforge.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfCard
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun McpScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MfHero(
            "MCP Workbench",
            "Профессиональные инструменты: файлы, сцена, GitHub, плагины. Локально или HTTPS/localhost.",
        )
        Text("Серверы", color = MfCyan, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.mcpServers.forEach { s ->
                MfButton(s.name, primary = vm.mcpServerId == s.id) { vm.mcpServerId = s.id }
            }
        }
        MfField(vm.mcpNewName, { vm.mcpNewName = it }, "Имя сервера")
        MfField(vm.mcpNewUrl, { vm.mcpNewUrl = it }, "URL (https:// или http://127.0.0.1)")
        MfField(vm.mcpNewToken, { vm.mcpNewToken = it }, "Bearer (опционально)", password = true)
        MfButton("＋ Сервер") { vm.addMcpServer() }

        Text("Tools", color = MfCyan, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.mcpTools.forEach { t ->
                MfButton(t.name, primary = vm.mcpTool == t.name) { vm.mcpTool = t.name }
            }
        }
        Text(vm.mcpTools.find { it.name == vm.mcpTool }?.description.orEmpty(), color = MfMuted, fontSize = 12.sp)
        MfField(vm.mcpArgs, { vm.mcpArgs = it }, "JSON args", singleLine = false, minLines = 3)
        MfButton("Выполнить", primary = true) { vm.runMcpTool() }
        MfCard {
            Text(vm.mcpOut, color = MfText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Text("Плагины", color = MfCyan, fontSize = 13.sp)
        vm.plugins().forEach { p ->
            Text("• ${p.title} — ${p.summary}", color = MfPurple, fontSize = 13.sp)
        }
    }
}
