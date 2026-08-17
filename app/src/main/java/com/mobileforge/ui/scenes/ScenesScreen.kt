package com.mobileforge.ui.scenes

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
import com.mobileforge.Section
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfCard
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenesScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MfHero("Scene workspace", "2D и 3D сцены хранятся в Scenes/*.scene.json. Превью — нативный Canvas.")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(vm.scenes, key = { it.name }) { sc ->
                MfCard {
                    Text("◇ ${sc.name}.scene.json", color = MfText, fontSize = 16.sp)
                    Text(
                        "${sc.dimension} • ${sc.objects.joinToString { it.name }}",
                        color = MfMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MfButton("Studio", primary = true) {
                            vm.scene = sc
                            vm.selected = sc.objects.firstOrNull()?.name
                            vm.openFile("Scenes/${sc.name}.scene.json")
                            vm.go(Section.Studio)
                        }
                        MfButton("▶ Play") {
                            vm.scene = sc
                            vm.startPlay()
                        }
                        MfButton("HTML preview") {
                            val html = vm.htmlPreview()
                            if (html != null) {
                                vm.importText = html
                                vm.dialog = "html"
                            }
                        }
                        MfButton("Удалить", danger = true) { vm.deleteScene(sc.name) }
                    }
                }
            }
            item {
                MfCard {
                    Text("＋ Новая сцена", color = MfText, fontSize = 16.sp)
                    Text("2D canvas или 3D арена с камерой.", color = MfMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MfButton("2D") { vm.dialog = "scene2d"; vm.dialogValue = "Menu" }
                        MfButton("3D", primary = true) { vm.dialog = "scene3d"; vm.dialogValue = "Arena" }
                    }
                }
            }
        }
    }
}
