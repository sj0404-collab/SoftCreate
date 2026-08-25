package com.mobileforge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.AppViewModel
import com.mobileforge.BuildConfig
import com.mobileforge.ui.common.MfButton
import com.mobileforge.ui.common.MfField
import com.mobileforge.ui.common.MfHero
import com.mobileforge.ui.theme.MfDanger
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfOk
import com.mobileforge.ui.theme.MfText

@Composable
fun SettingsScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MfHero(
            "Settings",
            "Ключи шифруются в Android Keystore (AES-GCM). Версия ${BuildConfig.VERSION_NAME}. UI полностью нативный.",
        )
        KeyRow("Zen API key (не нужен для free-линейки)", vm.hasZen)
        MfField(vm.zenKey, { vm.zenKey = it }, "опционально · free модели Zen без ключа", password = true)
        KeyRow("OpenRouter API key (free :free и платные)", vm.hasOr)
        MfField(vm.orKey, { vm.orKey = it }, "sk-or-v1-…  бесплатный аккаунт открывает :free", password = true)
        KeyRow("Local MCP token", vm.hasMcp)
        MfField(vm.mcpKey, { vm.mcpKey = it }, "Bearer token", password = true)
        KeyRow("Custom API key", vm.hasCustom)
        MfField(vm.customKey, { vm.customKey = it }, "optional", password = true)
        MfField(vm.customEndpoint, { vm.customEndpoint = it }, "Custom HTTPS endpoint")
        KeyRow("OrcaRouter (sk-orca-…)", vm.hasOrca)
        MfField(vm.orcaKey, { vm.orcaKey = it }, "sk-orca-… хранится только на устройстве", password = true)
        KeyRow("Gemini ключи (до 3, ротация при 429)", vm.hasGemini)
        MfField(vm.geminiKey1, { vm.geminiKey1 = it }, "AIza… ключ 1", password = true)
        MfField(vm.geminiKey2, { vm.geminiKey2 = it }, "AIza… ключ 2", password = true)
        MfField(vm.geminiKey3, { vm.geminiKey3 = it }, "AIza… ключ 3", password = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Save securely", primary = true) { vm.saveSettings() }
            MfButton("Check MCP") { vm.checkMcp() }
        }
        Text("MCP: 127.0.0.1:8765 · полный workbench во вкладке MCP", color = MfMuted, fontSize = 13.sp)
        Text("Плагины — вкладка Плагины (JS из Plugins/<id>/, не заглушки).", color = MfMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Чип моделей: Zen free без ключа. OpenRouter :free и paid — ключ выше. Orca/Gemini — свои ключи. Ключи только в Keystore, не в APK.",
            color = MfMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Репо и модели", color = MfText, fontSize = 15.sp)
        MfButton(if (vm.repoOnly) "Писать только в GitHub-репо" else "Локальный проект + GitHub") {
            vm.toggleRepoOnly(!vm.repoOnly)
        }
        MfField(vm.newModelId, { vm.newModelId = it }, "id модели (mimo-v2.5-free)")
        MfField(vm.newModelLabel, { vm.newModelLabel = it }, "подпись")
        MfField(vm.newModelProvider, { vm.newModelProvider = it }, "провайдер zen|openrouter|orca|gemini|custom")
        MfField(vm.newModelFormat, { vm.newModelFormat = it }, "json или xml")
        MfButton(if (vm.editModelUid == null) "Добавить модель" else "Сохранить правку", primary = true) { vm.saveUserModel() }
        vm.userModels.forEach { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${m.label} · ${m.id}", color = MfText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                MfButton("ред.") { vm.beginEditModel(m.uid) }
                MfButton("удал.") { vm.deleteUserModel(m.uid) }
            }
        }
        Text(
            "Сборка APK — GitHub Actions. PAT — Cloud. Выбранная модель не подменяется.",
            color = MfMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun KeyRow(label: String, has: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MfMuted, fontSize = 12.sp)
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (has) MfOk else MfDanger))
    }
}
