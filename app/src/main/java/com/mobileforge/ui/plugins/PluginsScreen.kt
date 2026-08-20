package com.mobileforge.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfCard
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfOk
import com.mobileforge.ui.theme.MfText

@Composable
fun PluginsScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MfHero(
            "Плагины",
            "JS из Plugins/<id>/plugin.json + main.js. Хуки onSave / onPlay / onAddObject вызываются с диска, не заглушки.",
        )
        MfField(vm.pluginNewId, { vm.pluginNewId = it }, "id, например scorepad")
        MfField(vm.pluginNewTitle, { vm.pluginNewTitle = it }, "название")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Создать плагин", primary = true) { vm.createPlugin() }
            MfButton("Пересканировать") { vm.reloadPlugins() }
        }
        if (vm.pluginRecords.isEmpty()) {
            Text("Нет Plugins/. Создайте плагин — появятся файлы и хуки.", color = MfMuted, fontSize = 13.sp)
        }
        vm.pluginRecords.forEach { p ->
            MfCard {
                Text(p.title, color = MfText, fontSize = 16.sp)
                Text("${p.id} · ${if (p.enabled) "вкл" else "выкл"}", color = MfCyan, fontSize = 12.sp)
                Text(p.summary, color = MfMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                Text("хуки: ${p.hooks.joinToString().ifBlank { "—" }}", color = MfMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    MfButton(if (p.enabled) "Выкл" else "Вкл") { vm.togglePlugin(p.id) }
                    MfButton("main.js") { vm.openFile("Plugins/${p.id}/${p.entry}") }
                    p.menus.forEach { menu ->
                        MfButton(menu.label) { vm.runPluginMenu(p.id, menu.id) }
                    }
                }
            }
        }
        Text("Лог хуков", color = MfOk, fontSize = 12.sp)
        vm.pluginLog.take(12).forEach { Text(it, color = MfMuted, fontSize = 11.sp) }
    }
}
