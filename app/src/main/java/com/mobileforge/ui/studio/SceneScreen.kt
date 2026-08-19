package com.mobileforge.ui.studio

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun SceneScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MfButton("Save") { vm.persistScene(true) }
            MfButton("▶ Play") { vm.startPlay(); vm.go(com.mobileforge.Section.Play) }
            MfButton("＋ Ground") { vm.addObject("Ground") }
            MfButton("＋ Player") { vm.addObject("Player") }
            MfButton("＋ Mesh") { vm.addObject("Mesh") }
            MfButton("＋ Coin") { vm.addObject("Coin") }
            MfButton("＋ Block") { vm.addObject("Block") }
            MfButton("＋ Light") { vm.addObject("Light") }
            MfButton("＋ Camera") { vm.addObject("Camera") }
            MfButton("Удалить", danger = true) { vm.deleteSelected() }
        }
        SceneViewport(
            scene = vm.scene,
            selected = vm.selected,
            orbit = vm.orbit,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.scene == null) {
                Text("Нет сцены. Откройте проект.", color = MfMuted, fontSize = 13.sp)
            }
            vm.scene?.objects.orEmpty().forEach { obj ->
                Text(
                    "${obj.name} · ${obj.type}",
                    color = if (obj.name == vm.selected) MfPurple else MfText,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.selectObject(obj.name) }.padding(6.dp),
                )
            }
        }
        InspectorPanel(vm, Modifier.fillMaxWidth().height(220.dp))
    }
}
