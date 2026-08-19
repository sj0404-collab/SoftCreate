package com.mobileforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.agent.AgentScreen
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.theme.MfBg

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
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            AgentScreen(vm)
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
                "file" -> MfField(vm.dialogValue, { vm.dialogValue = it }, "Путь, например Scripts/Player.cs")
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
