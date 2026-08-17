package com.mobileforge.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfCard
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfText
import com.mobileforge.ui.theme.MfYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MfHero(
            "Ваши проекты",
            "Нативный IDE: сцены, исходники, Play и AI. Всё пишется на диск устройства.",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("＋ Новый проект", onClick = { vm.dialog = "create" }, primary = true)
            MfButton("▣ SkyRunner demo", onClick = { vm.seedDemo() })
            MfButton("▣ Импорт JSON", onClick = { vm.importText = ""; vm.dialog = "import" })
        }
        if (vm.projects.isEmpty()) {
            MfCard { Text("Проектов пока нет. Создайте новый или откройте демо SkyRunner.", color = MfMuted) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.projects, key = { it.name }) { item ->
                    MfCard {
                        Text("◇", color = MfYellow, fontSize = 26.sp)
                        Text(item.name, color = MfText, fontSize = 16.sp)
                        Text(
                            (if (item.name == vm.projectName) "ACTIVE • " else "") + item.type.uppercase(),
                            color = MfCyan,
                            fontSize = 12.sp,
                        )
                        Text("Scripts · Scenes · Assets", color = MfMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MfButton("Открыть", onClick = { vm.openProject(item.name) }, primary = true)
                            MfButton("Экспорт") {
                                val json = vm.exportProject(item.name)
                                if (json != null) {
                                    vm.importText = json
                                    vm.dialog = "export"
                                }
                            }
                            MfButton("Удалить", onClick = { vm.deleteProject(item.name) }, danger = true)
                        }
                    }
                }
            }
        }
    }
}
