package com.mobileforge.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(vm: AppViewModel) {
    var field by remember { mutableStateOf(TextFieldValue(vm.editorText)) }
    LaunchedEffect(vm.openPath) {
        field = TextFieldValue(vm.editorText, TextRange(vm.editorText.length))
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
        val wide = maxWidth > 820.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExplorerPane(vm, Modifier.width(210.dp).fillMaxHeight())
                EditorPane(vm, field, { field = it }, Modifier.weight(1f).fillMaxHeight())
                ScenePane(vm, Modifier.width(300.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExplorerPane(vm, Modifier.fillMaxWidth().height(160.dp))
                EditorPane(vm, field, { field = it }, Modifier.fillMaxWidth().weight(1f))
                ScenePane(vm, Modifier.fillMaxWidth().height(360.dp))
            }
        }
    }
}

@Composable
private fun Pane(title: String, actions: @Composable () -> Unit = {}, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(MfPanel, RoundedCornerShape(11.dp))
            .border(1.dp, MfLine, RoundedCornerShape(11.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = MfText, fontSize = 12.sp)
            actions()
        }
        content()
    }
}

@Composable
private fun ExplorerPane(vm: AppViewModel, modifier: Modifier) {
    Pane("EXPLORER", { MfButton("＋") { vm.dialog = "file"; vm.dialogValue = "Scripts/New.js" } }, modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
            if (vm.files.isEmpty()) Text("Откройте проект", color = MfMuted, fontSize = 13.sp)
            vm.files.groupBy { it.path.substringBeforeLast('/', "/") }.forEach { (dir, list) ->
                Text(dir, color = MfText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                list.forEach { file ->
                    val active = file.path == vm.openPath
                    Text(
                        "◇ ${file.name}",
                        color = if (active) MfPurple else MfMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.openFile(file.path) }
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPane(
    vm: AppViewModel,
    field: TextFieldValue,
    onField: (TextFieldValue) -> Unit,
    modifier: Modifier,
) {
    Pane(
        (vm.openPath ?: "EDITOR").uppercase(),
        {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfButton("Save", primary = true) { vm.saveFile() }
                MfButton("Del", danger = true) { vm.deleteOpenFile() }
            }
        },
        modifier,
    ) {
        ForgeEditor(
            value = field,
            onValueChange = {
                onField(it)
                vm.onEditorChange(it.text, it.selection.end)
            },
            suggestions = vm.suggestions.toList(),
            onSuggestion = { item ->
                vm.applySuggestion(item)
                val text = vm.editorText
                onField(TextFieldValue(text, TextRange(vm.cursor.coerceIn(0, text.length))))
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenePane(vm: AppViewModel, modifier: Modifier) {
    Pane(
        "SCENE",
        {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MfButton("Save") { vm.persistScene(true) }
                MfButton("▶") { vm.startPlay() }
            }
        },
        modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            SceneViewport(
                scene = vm.scene,
                selected = vm.selected,
                orbit = vm.orbit,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            FlowRow(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MfButton("＋ Mesh") { vm.addObject("Mesh") }
                MfButton("＋ Player") { vm.addObject("Player") }
                MfButton("＋ Coin") { vm.addObject("Coin") }
                MfButton("＋ Light") { vm.addObject("Light") }
                MfButton("Удалить", danger = true) { vm.deleteSelected() }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp)) {
                vm.scene?.objects.orEmpty().forEach { obj ->
                    Text(
                        "${obj.name} · ${obj.type}",
                        color = if (obj.name == vm.selected) MfPurple else MfMuted,
                        modifier = Modifier.fillMaxWidth().clickable { vm.selectObject(obj.name) }.padding(6.dp),
                    )
                }
                Inspector(vm)
            }
        }
    }
}

@Composable
private fun Inspector(vm: AppViewModel) {
    val obj = vm.selectedObject()
    Text("INSPECTOR" + (obj?.let { " — ${it.name}" } ?: ""), color = MfText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    if (obj == null) {
        Text("Выберите объект", color = MfMuted, fontSize = 12.sp)
        return
    }
    fun setNum(current: Float, raw: String, apply: (Float) -> Unit) {
        raw.toFloatOrNull()?.let { apply(it) }
    }
    MfField(obj.name, { vm.updateSelected { o -> o.name = it } }, "name")
    MfField(obj.type, { vm.updateSelected { o -> o.type = it } }, "type")
    MfField(obj.x.toString(), { setNum(obj.x, it) { n -> vm.updateSelected { o -> o.x = n } } }, "x", numeric = true)
    MfField(obj.y.toString(), { setNum(obj.y, it) { n -> vm.updateSelected { o -> o.y = n } } }, "y", numeric = true)
    MfField(obj.z.toString(), { setNum(obj.z, it) { n -> vm.updateSelected { o -> o.z = n } } }, "z", numeric = true)
    MfField(obj.sx.toString(), { setNum(obj.sx, it) { n -> vm.updateSelected { o -> o.sx = n } } }, "sx", numeric = true)
    MfField(obj.sy.toString(), { setNum(obj.sy, it) { n -> vm.updateSelected { o -> o.sy = n } } }, "sy", numeric = true)
    MfField(obj.sz.toString(), { setNum(obj.sz, it) { n -> vm.updateSelected { o -> o.sz = n } } }, "sz", numeric = true)
    MfField(obj.color, { vm.updateSelected { o -> o.color = it } }, "color")
    MfField(obj.script, { vm.updateSelected { o -> o.script = it } }, "script")
    MfField(obj.speed.toString(), { setNum(obj.speed, it) { n -> vm.updateSelected { o -> o.speed = n } } }, "speed", numeric = true)
    MfButton(if (obj.solid) "Solid: ON" else "Solid: OFF") { vm.updateSelected { o -> o.solid = !o.solid } }
}
