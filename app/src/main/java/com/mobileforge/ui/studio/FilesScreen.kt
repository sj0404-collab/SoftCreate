package com.mobileforge.ui.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.Section
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfCard
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MfHero(
            vm.projectName ?: "Нет проекта",
            "Папка на телефоне:\n${vm.projectPath()}",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to "все", "Scripts" to "cs", "App" to "tsx", "Android" to "kt", "Blender" to "blend", "Assets" to "assets", "Export" to "export").forEach { (f, l) ->
                MfButton(l, primary = vm.fileFilter == f) { vm.fileFilter = f }
            }
            MfButton("Копировать путь", primary = true) { vm.copyProjectPath() }
            MfButton("＋ файл") { vm.dialog = "file"; vm.dialogValue = "Scripts/New.cs" }
        }
        if (vm.projectName == null) {
            Text("Откройте проект во вкладке Проекты.", color = MfMuted, fontSize = 14.sp)
            return
        }
        if (vm.files.isEmpty()) {
            Text("В проекте пока нет файлов.", color = MfMuted, fontSize = 14.sp)
        }
        val shown = vm.files.filter { vm.fileFilter.isBlank() || it.path.startsWith(vm.fileFilter) }
        shown.groupBy { it.path.substringBeforeLast('/', ".") }.forEach { (dir, list) ->
            Text(dir, color = MfCyan, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            list.forEach { file ->
                val active = file.path == vm.openPath
                MfCard(onClick = {
                    vm.openFile(file.path)
                    vm.go(Section.Ai)
                }) {
                    Text(
                        file.path,
                        color = if (active) MfPurple else MfText,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
