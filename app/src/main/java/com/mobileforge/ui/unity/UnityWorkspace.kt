package com.mobileforge.ui.unity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.Section
import com.mobileforge.ui.ai.AiScreen
import com.mobileforge.ui.assets.AssetsScreen
import com.mobileforge.ui.cloud.CloudScreen
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.mcp.McpScreen
import com.mobileforge.ui.play.PlayScreen
import com.mobileforge.ui.projects.ProjectsScreen
import com.mobileforge.ui.settings.SettingsScreen
import com.mobileforge.ui.studio.InspectorPanel
import com.mobileforge.ui.studio.SceneViewport
import com.mobileforge.ui.theme.MfBg
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun UnityWorkspace(vm: AppViewModel) {
    var leftTab by remember { mutableStateOf(0) }
    var overlay by remember { mutableStateOf<Section?>(null) }
    val playing = vm.runtime?.playing == true
    Column(Modifier.fillMaxSize().background(MfBg)) {
        Row(
            Modifier.fillMaxWidth().background(MfPanel).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("MF", color = MfPurple, fontSize = 14.sp)
            Text(vm.projectName ?: "No Project", color = MfText, fontSize = 13.sp)
            Text(vm.scene?.name ?: "—", color = MfCyan, fontSize = 11.sp)
            MfButton(if (playing) "■ Stop" else "▶ Play", primary = !playing) {
                if (playing) vm.stopPlay() else vm.startPlay()
            }
            MfButton("＋ Cube") { vm.addObject("Mesh") }
            MfButton("＋ Light") { vm.addObject("Light") }
            MfButton("＋ Cam") { vm.addObject("Camera") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MfButton("Projects") { overlay = if (overlay == Section.Projects) null else Section.Projects }
                MfButton("AI") { vm.go(Section.Agent) }
                MfButton("Cloud") { overlay = if (overlay == Section.Cloud) null else Section.Cloud }
                MfButton("MCP") { overlay = if (overlay == Section.Mcp) null else Section.Mcp }
                MfButton("Assets") { overlay = if (overlay == Section.Assets) null else Section.Assets }
                MfButton("Settings") { overlay = if (overlay == Section.Settings) null else Section.Settings }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(200.dp).fillMaxHeight().background(MfPanel).border(1.dp, MfLine)) {
                Row(Modifier.fillMaxWidth()) {
                    DockTab("Hierarchy", leftTab == 0) { leftTab = 0 }
                    DockTab("Project", leftTab == 1) { leftTab = 1 }
                }
                if (leftTab == 0) HierarchyDock(vm) else ProjectDock(vm)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(MfBg).border(1.dp, MfLine)) {
                    when {
                        overlay != null -> OverlayPanel(vm, overlay!!)
                        playing -> PlayScreen(vm, Modifier.fillMaxSize())
                        else -> SceneViewport(
                            scene = vm.scene,
                            selected = vm.selected,
                            orbit = vm.orbit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (overlay == null && !playing) {
                        Text("Scene", color = MfMuted, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                    if (playing && overlay == null) {
                        Text("Game", color = MfCyan, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().height(92.dp).background(MfPanel).border(1.dp, MfLine).padding(8.dp),
                ) {
                    Text("Console", color = MfMuted, fontSize = 11.sp)
                    vm.console.take(4).forEach {
                        Text(it, color = MfText, fontSize = 11.sp, maxLines = 1)
                    }
                    if (vm.console.isEmpty()) Text("Нет сообщений", color = MfMuted, fontSize = 11.sp)
                }
            }
            Column(Modifier.width(240.dp).fillMaxHeight().background(MfPanel).border(1.dp, MfLine)) {
                Text("Inspector", color = MfMuted, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                InspectorPanel(vm, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DockTab(title: String, on: Boolean, click: () -> Unit) {
    Text(
        title,
        color = if (on) MfPurple else MfMuted,
        fontSize = 12.sp,
        modifier = Modifier.clickable(onClick = click).padding(8.dp),
    )
}

@Composable
private fun HierarchyDock(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp)) {
        Text(vm.scene?.let { "${it.name}  ${it.dimension}" } ?: "Нет сцены", color = MfCyan, fontSize = 11.sp)
        vm.scene?.objects.orEmpty().forEach { obj ->
            Text(
                "${if (obj.enabled) "●" else "○"} ${obj.name}  ${obj.type}",
                color = if (obj.name == vm.selected) MfPurple else MfText,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.selectObject(obj.name) }.padding(vertical = 5.dp),
            )
        }
        if (vm.scene == null) Text("Откройте проект — объекты появятся здесь", color = MfMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ProjectDock(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp)) {
        if (vm.files.isEmpty()) Text("Нет файлов проекта", color = MfMuted, fontSize = 12.sp)
        vm.files.groupBy { it.path.substringBeforeLast('/', "/") }.forEach { (dir, list) ->
            Text(dir, color = MfCyan, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            list.forEach { file ->
                Text(
                    file.name,
                    color = if (file.path == vm.openPath) MfPurple else MfText,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clickable { vm.openFile(file.path) }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OverlayPanel(vm: AppViewModel, section: Section) {
    when (section) {
        Section.Projects -> ProjectsScreen(vm)
        Section.Ai -> AiScreen(vm)
        Section.Cloud -> CloudScreen(vm)
        Section.Mcp -> McpScreen(vm)
        Section.Assets -> AssetsScreen(vm)
        Section.Settings -> SettingsScreen(vm)
        else -> Box(Modifier.fillMaxSize())
    }
}
