package com.mobileforge.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfText

@Composable
fun CodeScreen(vm: AppViewModel) {
    var field by remember { mutableStateOf(TextFieldValue(vm.editorText)) }
    LaunchedEffect(vm.openPath, vm.editorText) {
        if (field.text != vm.editorText) {
            field = TextFieldValue(vm.editorText, TextRange(vm.editorText.length))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                vm.openPath ?: "файл не открыт — вкладка Файлы",
                color = if (vm.openPath == null) MfMuted else MfText,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            MfButton("Save", primary = true) { vm.saveFile() }
            MfButton("Del", danger = true) { vm.deleteOpenFile() }
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}
