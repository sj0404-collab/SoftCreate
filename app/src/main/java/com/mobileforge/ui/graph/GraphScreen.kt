package com.mobileforge.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.engine.VisualGraph
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun GraphScreen(vm: AppViewModel) {
    val g = vm.graph
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Ноды без кода", color = MfText, fontSize = 18.sp)
        Text("Создайте / замените блок. Привяжите к объекту — Play исполнит граф.", color = MfMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Новый граф игрока", primary = true) { vm.newPlayerGraph() }
            MfButton("Сохранить") { vm.saveGraph() }
            MfButton("На объект") { vm.bindGraphToSelected() }
        }
        MfField(vm.graphName, { vm.graphName = it }, "имя графа")
        Text("Палитра — нажмите чтобы добавить", color = MfMuted, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VisualGraph.KINDS.filter { it != "ReplaceKind" }.forEach { k ->
                MfButton("+ $k") { vm.addGraphNode(k) }
            }
        }
        if (g == null) {
            Text("Нет графа. «Новый граф игрока» или откройте Assets/Graphs.", color = MfMuted, fontSize = 13.sp)
            return@Column
        }
        Text("Связи: ${g.links.size}  ·  выбран: ${vm.graphSelected ?: "—"}", color = MfMuted, fontSize = 12.sp)
        g.nodes.forEach { n ->
            val on = vm.graphSelected == n.id
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MfPanel)
                    .border(1.dp, if (on) MfPurple else MfLine, RoundedCornerShape(10.dp))
                    .clickable { vm.graphSelected = n.id }
                    .padding(10.dp),
            ) {
                Text("${n.kind}  ·  ${n.id}", color = MfText, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VisualGraph.KINDS.take(8).forEach { k ->
                        MfButton(k) { vm.replaceGraphNode(n.id, k) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MfButton("связать со след.") { vm.linkToNext(n.id) }
                    MfButton("удалить") { vm.removeGraphNode(n.id) }
                }
            }
        }
        Text("Связи: " + g.links.joinToString { "${it.from}→${it.to}" }, color = MfMuted, fontSize = 11.sp)
    }
}
