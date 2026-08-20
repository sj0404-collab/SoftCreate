package com.mobileforge.ui.ide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.engine.ChangeLog
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfDanger
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfOk
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChangesScreen(vm: AppViewModel) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Изменения", color = MfPurple, fontSize = 20.sp)
        Text("Что агент, вы или плагин записали на диск. Нажмите строку — полный diff.", color = MfMuted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Обновить") { vm.reloadChanges() }
            MfButton("Очистить список") { vm.clearChanges() }
        }
        if (vm.changes.isEmpty()) {
            Text("Пока нет записей. Сохраните файл или дайте агенту fs.write.", color = MfMuted, fontSize = 13.sp)
        }
        vm.changes.forEach { ch ->
            val on = vm.selectedChange == ch.id
            Column(
                Modifier.fillMaxWidth().clickable { vm.selectedChange = if (on) 0L else ch.id }.padding(vertical = 6.dp),
            ) {
                Text(ch.path, color = if (on) MfCyan else MfText, fontSize = 14.sp)
                Text(
                    "${ch.author} · ${fmt.format(Date(ch.at))} · ${ChangeLog.summary(ch)}",
                    color = MfMuted,
                    fontSize = 11.sp,
                )
                if (on) {
                    ChangeLog.diff(ch.before, ch.after).take(200).forEach { line ->
                        val color = when (line.kind) {
                            '+' -> MfOk
                            '-' -> MfDanger
                            else -> MfMuted
                        }
                        Text("${line.kind}${line.text}", color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
