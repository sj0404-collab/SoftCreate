package com.mobileforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.Section
import com.mobileforge.ui.ai.AiScreen
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.play.PlayScreen
import com.mobileforge.ui.projects.ProjectsScreen
import com.mobileforge.ui.scenes.ScenesScreen
import com.mobileforge.ui.settings.SettingsScreen
import com.mobileforge.ui.studio.StudioScreen
import com.mobileforge.ui.theme.MfBg
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

private val tabs = listOf(
    Section.Projects to ("▣" to "Проекты"),
    Section.Studio to ("⌘" to "Studio"),
    Section.Scenes to ("◇" to "Сцены"),
    Section.Play to ("▶" to "Play"),
    Section.Ai to ("✦" to "AI"),
    Section.Settings to ("⚙" to "Настройки"),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) {
        val msg = vm.toast ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        vm.toast = null
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MfBg,
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MfPanel)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                tabs.forEach { (section, pair) ->
                    val (icon, label) = pair
                    val active = vm.section == section
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.go(section) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(icon, color = if (active) MfPurple else MfMuted, fontSize = 18.sp)
                        Text(label, color = if (active) MfText else MfMuted, fontSize = 10.sp)
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MfPanel)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("MF", color = MfPurple, fontSize = 20.sp)
                Spacer(Modifier.height(16.dp))
                tabs.forEach { (section, pair) ->
                    val (icon, _) = pair
                    val active = vm.section == section
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(if (active) MfLine.copy(alpha = 0.5f) else MfPanel)
                            .clickable { vm.go(section) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, color = if (active) MfText else MfMuted, fontSize = 20.sp)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().background(MfBg).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("MobileForge Studio", color = MfText, fontSize = 21.sp)
                        Text(
                            vm.section.name.uppercase() + (vm.projectName?.let { " • $it" } ?: ""),
                            color = MfCyan,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        if (vm.dirty) "● не сохранено" else "native ${com.mobileforge.BuildConfig.VERSION_NAME}",
                        color = MfMuted,
                        fontSize = 12.sp,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (vm.section) {
                        Section.Projects -> ProjectsScreen(vm)
                        Section.Studio -> StudioScreen(vm)
                        Section.Scenes -> ScenesScreen(vm)
                        Section.Play -> PlayScreen(vm)
                        Section.Ai -> AiScreen(vm)
                        Section.Settings -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
    Dialogs(vm)
}

@Composable
private fun Dialogs(vm: AppViewModel) {
    val kind = vm.dialog ?: return
    val title = when (kind) {
        "create" -> "Новый проект"
        "file" -> "Новый файл"
        "import" -> "Импорт JSON"
        "export" -> "Экспорт проекта"
        "html" -> "HTML preview (файл, не UI)"
        "scene2d" -> "Сцена 2D"
        "scene3d" -> "Сцена 3D"
        else -> "Диалог"
    }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text(title) },
        text = {
            when (kind) {
                "create" -> {
                    Column {
                        MfField(vm.dialogValue, { vm.dialogValue = it }, "Имя проекта")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            MfButton("3D", primary = true) { vm.createProject(vm.dialogValue, "3d"); vm.dialog = null }
                            MfButton("2D") { vm.createProject(vm.dialogValue, "2d"); vm.dialog = null }
                        }
                    }
                }
                "file" -> MfField(vm.dialogValue, { vm.dialogValue = it }, "Путь, например Scripts/Enemy.js")
                "scene2d", "scene3d" -> MfField(vm.dialogValue, { vm.dialogValue = it }, "Имя сцены")
                else -> MfField(vm.importText, { vm.importText = it }, "JSON / HTML", singleLine = false, minLines = 8)
            }
        },
        confirmButton = {
            if (kind == "create") {
                MfButton("Закрыть") { vm.dialog = null }
            } else {
                MfButton("OK", primary = true) {
                    when (kind) {
                        "file" -> vm.createFile(vm.dialogValue)
                        "import" -> vm.importProject(vm.importText)
                        "scene2d" -> vm.createScene(vm.dialogValue, "2D")
                        "scene3d" -> vm.createScene(vm.dialogValue, "3D")
                    }
                    vm.dialog = null
                }
            }
        },
        dismissButton = { MfButton("Отмена") { vm.dialog = null } },
    )
}
