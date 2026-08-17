package com.mobileforge.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfText

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MfHero("AI Code Agent", "Провайдер вызывается нативно. Apply пишет файлы только после Review.")
        Text("Провайдер", color = MfMuted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("zen" to "Zen Direct", "openrouter" to "OpenRouter", "mcp" to "Local MCP", "custom" to "Custom API").forEach { (id, label) ->
                MfButton(label, primary = vm.provider == id) {
                    vm.provider = id
                    vm.notify("Провайдер: $label")
                }
            }
        }
        MfField(vm.customEndpoint, { vm.customEndpoint = it }, "Custom HTTPS endpoint")
        Dropdown("Модель", vm.models, vm.model) { vm.model = it }
        MfField(vm.customModel, { vm.customModel = it }, "или свой model id")
        Dropdown("Событие", listOf("ON_START", "ON_UPDATE", "ON_COLLISION_ENTER", "ON_BUTTON_CLICK", "ON_SCENE_LOADED"), vm.aiEvent) { vm.aiEvent = it }
        Dropdown("Язык", listOf("JavaScript", "C#", "Kotlin", "C++", "GLSL", "JSON"), vm.aiLanguage) { vm.aiLanguage = it }
        MfField(vm.aiTask, { vm.aiTask = it }, "Задача", singleLine = false, minLines = 4)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("✦ Generate", primary = true, enabled = !vm.aiBusy) { vm.generateAi() }
            MfButton("Review") { if (vm.proposal.isEmpty()) vm.notify("Нет proposal") else vm.notify("Проверьте diff и Apply") }
            MfButton("Apply") { vm.applyProposal() }
        }
        Text(
            vm.aiOut,
            color = MfText,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(MfPanel, RoundedCornerShape(8.dp))
                .border(1.dp, MfLine, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
        vm.proposal.forEach { file ->
            Text(file.path + if (file.previous.isBlank()) " (new)" else "", color = MfText, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(Modifier.weight(1f).padding(end = 6.dp).heightIn(min = 80.dp).background(MfPanel).padding(8.dp)) {
                    Text(file.previous.ifBlank { "(нет файла)" }, color = MfMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Column(Modifier.weight(1f).heightIn(min = 80.dp).background(MfPanel).padding(8.dp)) {
                    Text(file.content, color = MfText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, items: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = !open }) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item); open = false })
            }
        }
    }
}
