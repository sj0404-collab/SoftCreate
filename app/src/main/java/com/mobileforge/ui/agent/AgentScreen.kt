package com.mobileforge.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.BuildConfig
import com.mobileforge.Section
import com.mobileforge.agent.AgentCard
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0C0C0E)
private val Bar = Color(0xFF141416)
private val Pill = Color(0xFF1C1C20)
private val Teal = Color(0xFF2EE6C5)
private val Mute = Color(0xFF8B8B93)
private val Ink = Color(0xFFEDEDEF)
private val Line = Color(0xFF2A2A30)
private val CardBg = Color(0xFF16161A)
private val Ok = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF6B7A)

@Composable
fun AgentScreen(vm: AppViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.agentRunning) {
        while (true) {
            delay(1000)
            tick++
            if (vm.agentRunning) vm.agentElapsed += 0
        }
    }
    val session = formatMin(vm.sessionElapsedMs() + tick * 0L)
    val left = formatMin(vm.sessionLeftMs())
    val limit = formatMin(vm.sessionLimitMs)
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Bar).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Zen", color = Teal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("Agent", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            ModelChip(vm)
            Spacer(Modifier.weight(1f))
            Box {
                IconBtn("☰") { vm.agentMenu = true }
                DropdownMenu(expanded = vm.agentMenu, onDismissRequest = { vm.agentMenu = false }) {
                    listOf(
                        Section.Studio to "Редактор Unity",
                        Section.Projects to "Проекты",
                        Section.Cloud to "Cloud / GitHub",
                        Section.Mcp to "MCP",
                        Section.Settings to "Настройки",
                    ).forEach { (sec, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { vm.agentMenu = false; vm.go(sec) })
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            IconBtn("↓") { exportSession(vm) }
            Spacer(Modifier.width(6.dp))
            IconBtn("■", active = vm.agentRunning) { vm.stopAgent() }
        }
        Column(Modifier.fillMaxWidth().background(Bar).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (vm.agentRunning) Teal else Ok))
                Spacer(Modifier.width(8.dp))
                Text(vm.agentStatus, color = Ink, fontSize = 13.sp)
                Meta("раунд", if (vm.agentRound == 0) "—" else vm.agentRound.toString())
                Meta("инстр.", vm.agentToolsUsed.toString())
                Meta("токены", vm.agentTokens.toString())
                Text("${vm.agentElapsed}с", color = Mute, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("сессия $session / $limit · осталось $left", color = Mute, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("сборка ${BuildConfig.VERSION_CODE}.${BuildConfig.VERSION_NAME}", color = Mute, fontSize = 13.sp)
        }
        if (vm.agentCards.isEmpty()) {
            Column(Modifier.weight(1f).padding(22.dp)) {
                Text(
                    "Готов. Опишите задачу — каждый вызов инструмента появится здесь карточкой: что запущено, сколько заняло и что вернуло. Нажмите на карточку, чтобы раскрыть.",
                    color = Ink,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    "«Стоп» прерывает в любой момент. Агент сам создаёт проекты, файлы, сцены и управление — вы только ставите задачу.",
                    color = Mute,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.agentCards, key = { it.id }) { card -> ToolCard(card) { vm.toggleCard(card.id) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Bg).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Pill)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (vm.agentInput.isEmpty()) Text("Задача...", color = Mute, fontSize = 16.sp)
                BasicTextField(
                    value = vm.agentInput,
                    onValueChange = { vm.agentInput = it },
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(Teal),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.submitAgent() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Teal)
                    .clickable(enabled = !vm.agentRunning) { vm.submitAgent() },
                contentAlignment = Alignment.Center,
            ) {
                Text("→", color = Color(0xFF06261F), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModelChip(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Line, RoundedCornerShape(20.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prettyModel(vm.model), color = Ink, fontSize = 14.sp)
            Text("  ↕", color = Mute, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                "zen" to listOf("laguna-s-2.1-free", "deepseek-v4-flash-free", "mimo-v2.5-free", "nemotron-3-ultra-free"),
                "orca" to listOf("orcarouter/auto"),
                "gemini" to listOf("gemini-2.0-flash", "gemini-2.0-flash-lite"),
            ).forEach { (prov, ids) ->
                ids.forEach { id ->
                    DropdownMenuItem(
                        text = { Text("${prettyModel(id)} · $prov") },
                        onClick = { vm.provider = prov; vm.model = id; open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBtn(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(if (active) Pill else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ink, fontSize = 16.sp) }
}

@Composable
private fun Meta(k: String, v: String) {
    Text("  $k ", color = Mute, fontSize = 13.sp)
    Text(v, color = Ink, fontSize = 13.sp)
}

@Composable
private fun ToolCard(card: AgentCard, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (card.ok) Ok else Bad))
            Spacer(Modifier.width(8.dp))
            Text(card.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${card.ms}ms", color = Mute, fontSize = 12.sp)
        }
        if (card.expanded) {
            Spacer(Modifier.height(8.dp))
            Text(card.args, color = Mute, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            Text(card.result.take(2000), color = Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        } else {
            Text(card.result.take(80).replace("\n", " "), color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun prettyModel(id: String): String = when {
    id.contains("laguna", true) -> "Laguna S 2.1"
    id.contains("deepseek-v4-flash-free") -> "DeepSeek Flash free"
    id.contains("deepseek") -> "DeepSeek Flash"
    id.contains("mimo") -> "MiMo V2.5"
    id.contains("nemotron") -> "Nemotron 3"
    id.contains("north") -> "North Mini"
    id.contains("orcarouter") || id == "auto" -> "Orca Auto"
    id.contains("gemini-2.0-flash-lite") -> "Gemini Flash Lite"
    id.contains("gemini-2.0") -> "Gemini 2.0 Flash"
    id.contains("gemini") -> "Gemini"
    else -> id.substringAfterLast('/').take(22)
}

private fun formatMin(ms: Long): String {
    val total = (ms / 60000).toInt()
    val h = total / 60
    val m = total % 60
    return "${h}ч ${m}м"
}

private fun exportSession(vm: AppViewModel) {
    val text = vm.agentCards.asReversed().joinToString("\n\n") {
        "## ${it.title} (${it.ms}ms)\n${it.args}\n${it.result}"
    }.ifBlank { "empty session" }
    vm.importText = text
    vm.dialog = "export"
    vm.notify("Сессия готова к копированию")
}
