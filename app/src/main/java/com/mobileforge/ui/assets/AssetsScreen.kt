package com.mobileforge.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun AssetsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MfHero("Asset Database", "Проект + StudioPack с runner. Модели, материалы, префабы, .cs. Сборка APK не на телефоне.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("＋ C#", primary = true) { vm.dialog = "file"; vm.dialogValue = "Scripts/NewBehaviour.cs" }
            MfButton("＋ Material") { vm.dialog = "file"; vm.dialogValue = "Materials/New.mat" }
            MfButton("＋ Model") { vm.dialog = "file"; vm.dialogValue = "Models/Mesh.obj" }
            MfButton("＋ Prefab") { vm.dialog = "file"; vm.dialogValue = "Prefabs/Entity.json" }
        }
        Text("Проект", color = MfCyan, fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.files, key = { it.path }) { file ->
                MfCard(onClick = {
                    vm.openFile(file.path)
                    vm.go(Section.Studio)
                }) {
                    Text(file.path, color = if (file.path == vm.openPath) MfPurple else MfText, fontSize = 13.sp)
                    Text(file.name.substringAfterLast('.', "file"), color = MfMuted, fontSize = 11.sp)
                }
            }
            item { Text("StudioPack (вшит в APK runner-ом)", color = MfCyan, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            if (vm.pack.isEmpty()) {
                item { Text("Pack появится в сборке с GitHub runner (generateStudioPack).", color = MfMuted, fontSize = 12.sp) }
            } else {
                items(vm.pack.take(80), key = { it.path }) { item ->
                    Text(
                        item.path,
                        color = MfMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 3.dp).clickable { vm.log("pack ${item.path}") },
                    )
                }
            }
        }
    }
}
