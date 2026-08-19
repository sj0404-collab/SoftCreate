package com.mobileforge.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.BuildConfig
import com.mobileforge.Section
import com.mobileforge.agent.AgentEvent
import com.mobileforge.ai.ModelCatalog
import com.mobileforge.ai.StreamText
import com.mobileforge.ui.assets.AssetsScreen
import com.mobileforge.ui.cloud.CloudScreen
import com.mobileforge.ui.mcp.McpScreen
import com.mobileforge.ui.play.PlayScreen
import com.mobileforge.ui.projects.ProjectsScreen
import com.mobileforge.ui.settings.SettingsScreen
import com.mobileforge.ui.studio.CodeScreen
import com.mobileforge.ui.studio.FilesScreen
import com.mobileforge.ui.studio.SceneScreen
import com.mobileforge.ui.unity.UnityWorkspace
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0C0C0E)
private val Bar = Color(0xFF141416)
private val Pill = Color(0xFF1C1C20)
private val Teal = Color(0xFF2EE6C5)
private val Mute = Color(0xFF8B8B93)
private val Ink = Color(0xFFEDEDEF)
private val Line = Color(0xFF2A2A30)
private val CardBg = Color(0xFF16161A)
private val UserBg = Color(0xFF16352F)
private val Ok = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF6B7A)
private val Think = Color(0xFF9AA0B5)

private val tabs = listOf(
    Section.Agent to ("✦" to "Агент"),
    Section.Projects to ("▣" to "Проекты"),
    Section.Files to ("▤" to "Файлы"),
    Section.Studio to ("⌘" to "Сцена"),
    Section.Ai to ("✎" to "Код"),
    Section.Assets to ("◇" to "Ассеты"),
    Section.Play to ("▶" to "Play"),
    Section.Cloud to ("☁" to "Cloud"),
    Section.Mcp to ("⚒" to "MCP"),
    Section.Settings to ("⚙" to "Ещё"),
)

@Composable
fun AgentScreen(vm: AppViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val session = formatMin(vm.sessionElapsedMs() + tick * 0L)
    val limit = formatMin(vm.sessionLimitMs)
    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().background(Bar).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zen", color = Teal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text("Agent", color = Ink, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                ModelChip(vm)
                Spacer(Modifier.weight(1f))
                IconBtn("↓") { exportSession(vm) }
                Spacer(Modifier.width(6.dp))
                IconBtn("■", active = vm.agentRunning) { vm.stopAgent() }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.agentRunning) Teal else Ok))
                Spacer(Modifier.width(6.dp))
                Text(vm.agentStatus, color = Ink, fontSize = 12.sp)
                Meta("раунд", if (vm.agentRound == 0) "—" else vm.agentRound.toString())
                Meta("инстр.", vm.agentToolsUsed.toString())
                Meta("токены", vm.agentTokens.toString())
                Text(" ${vm.agentElapsed}с", color = Mute, fontSize = 12.sp)
            }
            Text(
                buildString {
                    append("сессия $session / $limit · ${BuildConfig.VERSION_NAME}")
                    vm.projectName?.let { append(" · $it") }
                },
                color = Mute,
                fontSize = 11.sp,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.section) {
                Section.Agent -> ChatPane(vm)
                Section.Projects -> ProjectsScreen(vm)
                Section.Files -> FilesScreen(vm)
                Section.Studio -> {
                    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    if (land) UnityWorkspace(vm) else SceneScreen(vm)
                }
                Section.Ai -> CodeScreen(vm)
                Section.Assets -> AssetsScreen(vm)
                Section.Play -> PlayScreen(vm)
                Section.Cloud -> CloudScreen(vm)
                Section.Mcp -> McpScreen(vm)
                Section.Settings -> SettingsScreen(vm)
            }
        }
        if (vm.section == Section.Agent) InputBar(vm)
        TabStrip(vm)
    }
}

@Composable
private fun TabStrip(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bar)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (sec, pair) ->
            val (icon, label) = pair
            val on = vm.section == sec
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Pill else Color.Transparent)
                    .clickable {
                        if (sec == Section.Play) vm.startPlay()
                        vm.go(sec)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, color = if (on) Teal else Mute, fontSize = 14.sp)
                Text(label, color = if (on) Ink else Mute, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ChatPane(vm: AppViewModel) {
    val state = rememberLazyListState()
    LaunchedEffect(vm.agentFeed.size, vm.streamTick) {
        if (vm.agentFeed.isNotEmpty()) {
            state.animateScrollToItem(vm.agentFeed.lastIndex)
        }
    }
    if (vm.agentFeed.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                "Готов. Напишите задачу — своё сообщение останется в ленте, ответ и размышления пойдут стримом, инструменты карточками.",
                color = Ink,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
        }
        return
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(vm.agentFeed, key = { it.id }) { ev ->
            when (ev) {
                is AgentEvent.User -> UserBubble(ev.text)
                is AgentEvent.Assistant -> AssistantBubble(ev)
                is AgentEvent.Tool -> ToolCard(ev) { vm.toggleCard(ev.id) }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = Ink,
            fontSize = 15.sp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(UserBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AssistantBubble(ev: AgentEvent.Assistant) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(12.dp),
    ) {
        val think = StreamText.stripNullSpam(ev.thinking)
        val body = StreamText.stripNullSpam(ev.text)
        if (think.isNotBlank()) {
            Text("размышление", color = Think, fontSize = 11.sp)
            Text(think, color = Think, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (body.isNotBlank()) {
            Text(body, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
        } else if (ev.live) {
            Text("печатает…", color = Mute, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToolCard(ev: AgentEvent.Tool, onToggle: () -> Unit) {
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
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ev.ok) Ok else Bad))
            Spacer(Modifier.width(8.dp))
            Text(ev.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${ev.ms}ms", color = Mute, fontSize = 12.sp)
        }
        if (ev.expanded) {
            Spacer(Modifier.height(8.dp))
            Text(ev.args, color = Mute, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            Text(ev.result.take(2000), color = Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        } else {
            Text(ev.result.take(90).replace("\n", " "), color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun InputBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Bg).padding(10.dp),
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

@Composable
private fun ModelChip(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Line, RoundedCornerShape(18.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ModelCatalog.pretty(vm.model), color = Ink, fontSize = 13.sp)
            Text(" ↕", color = Mute, fontSize = 11.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            ModelCatalog.groups.forEach { (title, models) ->
                DropdownMenuItem(
                    text = { Text(title, color = Mute, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    onClick = {},
                    enabled = false,
                )
                models.forEach { item ->
                    val locked = item.needKey && when (item.providerId) {
                        "openrouter" -> !vm.hasOr
                        "orca" -> !vm.hasOrca
                        "gemini" -> !vm.hasGemini
                        "zen" -> !item.free && !vm.hasZen
                        else -> false
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                buildString {
                                    append(item.label)
                                    if (item.free) append(" · free") else append(" · $")
                                    if (locked) append(" · ключ")
                                },
                                fontSize = 13.sp,
                            )
                        },
                        onClick = {
                            vm.setRoute(item.providerId, item.id)
                            open = false
                            if (locked) vm.notify("Ключ ${item.providerId} — вкладка Ещё")
                        },
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
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .background(if (active) Pill else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ink, fontSize = 14.sp) }
}

@Composable
private fun Meta(k: String, v: String) {
    Text("  $k ", color = Mute, fontSize = 12.sp)
    Text(v, color = Ink, fontSize = 12.sp)
}

private fun formatMin(ms: Long): String {
    val total = (ms / 60000).toInt()
    return "${total / 60}ч ${total % 60}м"
}

private fun exportSession(vm: AppViewModel) {
    val text = vm.agentFeed.joinToString("\n\n") { ev ->
        when (ev) {
            is AgentEvent.User -> "USER: ${ev.text}"
            is AgentEvent.Assistant -> "THINK:\n${ev.thinking}\n\nASSISTANT:\n${ev.text}"
            is AgentEvent.Tool -> "TOOL ${ev.title} (${ev.ms}ms)\n${ev.result}"
        }
    }.ifBlank { "empty session" }
    vm.importText = text
    vm.dialog = "export"
    vm.notify("Сессия готова к копированию")
}
