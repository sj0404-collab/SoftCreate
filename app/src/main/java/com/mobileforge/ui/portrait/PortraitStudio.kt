package com.mobileforge.ui.portrait

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AiCommand
import com.mobileforge.AppViewModel
import com.mobileforge.Section
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.play.PlayScreen
import com.mobileforge.ui.studio.ForgeEditor
import com.mobileforge.ui.theme.MfBg
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun PortraitStudio(vm: AppViewModel) {
    if (vm.runtime?.playing == true) {
        Column(Modifier.fillMaxSize().background(MfBg)) {
            Row(
                Modifier.fillMaxWidth().background(MfPanel).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Game", color = MfCyan, fontSize = 13.sp)
                MfButton("■ Stop") { vm.stopPlay() }
            }
            PlayScreen(vm, Modifier.weight(1f))
        }
        return
    }
    var field by remember { mutableStateOf(TextFieldValue(vm.editorText)) }
    LaunchedEffect(vm.openPath) {
        field = TextFieldValue(vm.editorText, TextRange(vm.editorText.length))
    }
    Column(Modifier.fillMaxSize().background(MfBg)) {
        Row(
            Modifier.fillMaxWidth().background(MfPanel).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("MF", color = MfPurple, fontSize = 16.sp)
            Text(vm.projectName ?: "нет проекта", color = MfText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            MfButton("▶") { vm.startPlay() }
        }
        Row(
            Modifier.fillMaxWidth().background(MfPanel).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                Section.Projects to "Проекты",
                Section.Cloud to "Cloud",
                Section.Mcp to "MCP",
                Section.Settings to "Настройки",
            ).forEach { (sec, label) ->
                MfButton(label, primary = vm.section == sec) { vm.go(sec) }
            }
        }
        if (vm.section != Section.Studio && vm.section != Section.Ai && vm.section != Section.Play) {
            BoxFill(vm)
            return
        }
        Column(
            Modifier.weight(0.42f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("AI · только код", color = MfCyan, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiCommand.entries.forEach { cmd ->
                    MfButton(cmd.name, primary = vm.aiCommand == cmd) { vm.aiCommand = cmd }
                }
            }
            MfField(vm.aiTask, { vm.aiTask = it }, "Приказ режиссёра", singleLine = false, minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfButton("Generate", primary = true, enabled = !vm.aiBusy) { vm.generateAi() }
                MfButton("Apply") { vm.applyProposal() }
            }
            Text(vm.aiOut.take(500), color = MfText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.weight(0.58f).fillMaxWidth().background(MfPanel)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vm.openPath ?: "код", color = MfMuted, fontSize = 11.sp)
                MfButton("Save", primary = true) { vm.saveFile() }
                vm.files.take(12).forEach { f ->
                    Text(
                        f.name,
                        color = if (f.path == vm.openPath) MfPurple else MfMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { vm.openFile(f.path) }.padding(4.dp),
                    )
                }
            }
            ForgeEditor(
                value = field,
                onValueChange = {
                    field = it
                    vm.onEditorChange(it.text, it.selection.end)
                },
                suggestions = vm.suggestions.toList(),
                onSuggestion = { item ->
                    vm.applySuggestion(item)
                    val text = vm.editorText
                    field = TextFieldValue(text, TextRange(vm.cursor.coerceIn(0, text.length)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoxFill(vm: AppViewModel) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        when (vm.section) {
            Section.Projects -> com.mobileforge.ui.projects.ProjectsScreen(vm)
            Section.Cloud -> com.mobileforge.ui.cloud.CloudScreen(vm)
            Section.Mcp -> com.mobileforge.ui.mcp.McpScreen(vm)
            Section.Settings -> com.mobileforge.ui.settings.SettingsScreen(vm)
            else -> {}
        }
    }
}
