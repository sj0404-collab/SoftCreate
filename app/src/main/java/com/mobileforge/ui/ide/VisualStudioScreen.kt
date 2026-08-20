package com.mobileforge.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.studio.ForgeEditor
import com.mobileforge.ui.theme.MfBg
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfDanger
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfOk
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun VisualStudioScreen(vm: AppViewModel) {
    var field by remember { mutableStateOf(TextFieldValue(vm.editorText)) }
    var dock by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.openPath, vm.editorText) {
        if (field.text != vm.editorText) {
            field = TextFieldValue(vm.editorText, TextRange(vm.editorText.length.coerceAtLeast(0)))
        }
    }
    Column(Modifier.fillMaxSize().background(MfBg)) {
        Row(
            Modifier.fillMaxWidth().background(MfPanel).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("VS", color = MfPurple, fontSize = 13.sp)
            Text(vm.projectName ?: "нет проекта", color = MfText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            MfButton("Save", primary = true) { vm.saveFile() }
            MfButton("Find") { vm.findInProject() }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.width(150.dp).fillMaxHeight().background(MfPanel).border(1.dp, MfLine)
                    .verticalScroll(rememberScrollState()).padding(6.dp),
            ) {
                Text("Solution", color = MfCyan, fontSize = 11.sp)
                if (vm.files.isEmpty()) Text("откройте проект", color = MfMuted, fontSize = 11.sp)
                vm.files.groupBy { it.path.substringBeforeLast('/', ".") }.forEach { (dir, list) ->
                    Text(dir, color = MfMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
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
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().background(MfPanel).horizontalScroll(rememberScrollState()).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (vm.ideTabs.isEmpty()) Text("нет вкладок", color = MfMuted, fontSize = 11.sp)
                    vm.ideTabs.forEach { path ->
                        Text(
                            path.substringAfterLast('/'),
                            color = if (path == vm.openPath) MfCyan else MfText,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(if (path == vm.openPath) MfBg else MfPanel)
                                .clickable { vm.openFile(path) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                ForgeEditor(
                    value = field,
                    onValueChange = {
                        field = it
                        vm.onEditorChange(it.text, it.selection.end)
                    },
                    suggestions = vm.suggestions.toList(),
                    onSuggestion = { item ->
                        vm.applySuggestion(item)
                        val text = vm.editorText
                        field = TextFieldValue(text, TextRange(vm.cursor.coerceIn(0, text.length)))
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        Column(Modifier.fillMaxWidth().height(130.dp).background(MfPanel).border(1.dp, MfLine)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Error List", color = if (dock == 0) MfCyan else MfMuted, fontSize = 11.sp, modifier = Modifier.clickable { dock = 0 })
                Text("Output", color = if (dock == 1) MfCyan else MfMuted, fontSize = 11.sp, modifier = Modifier.clickable { dock = 1 })
                Text("Find", color = if (dock == 2) MfCyan else MfMuted, fontSize = 11.sp, modifier = Modifier.clickable { dock = 2 })
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                when (dock) {
                    0 -> {
                        if (vm.ideErrors.isEmpty()) Text("ошибок нет", color = MfOk, fontSize = 12.sp)
                        vm.ideErrors.forEach { Text(it, color = MfDanger, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    }
                    1 -> {
                        if (vm.console.isEmpty()) Text("пусто", color = MfMuted, fontSize = 11.sp)
                        vm.console.take(12).forEach { Text(it, color = MfText, fontSize = 11.sp, maxLines = 1) }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicTextField(
                                value = vm.findQuery,
                                onValueChange = { vm.findQuery = it },
                                textStyle = TextStyle(color = MfText, fontSize = 13.sp),
                                cursorBrush = SolidColor(MfCyan),
                                modifier = Modifier.weight(1f).background(MfBg).padding(6.dp),
                            )
                            MfButton("Найти") { vm.findInProject() }
                        }
                        vm.findHits.forEach { Text(it, color = MfText, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        if (vm.findHits.isEmpty() && vm.findQuery.isNotBlank()) Text("нет совпадений", color = MfMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
