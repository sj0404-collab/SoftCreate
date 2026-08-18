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
        KeyRow("Zen API key", vm.hasZen)
        MfField(vm.zenKey, { vm.zenKey = it }, "sk-…", password = true)
        KeyRow("OpenRouter API key", vm.hasOr)
        MfField(vm.orKey, { vm.orKey = it }, "sk-or-…", password = true)
        KeyRow("Local MCP token", vm.hasMcp)
        MfField(vm.mcpKey, { vm.mcpKey = it }, "Bearer token", password = true)
        KeyRow("Custom API key", vm.hasCustom)
        MfField(vm.customKey, { vm.customKey = it }, "optional", password = true)
        MfField(vm.customEndpoint, { vm.customEndpoint = it }, "Custom HTTPS endpoint")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Save securely", primary = true) { vm.saveSettings() }
            MfButton("Check MCP") { vm.checkMcp() }
        }
        Text("MCP: 127.0.0.1:8765 · полный workbench во вкладке MCP", color = MfMuted, fontSize = 13.sp)
        Text("Плагины", color = MfMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        vm.plugins().forEach { p ->
            Text("• ${p.title}: ${p.summary}", color = MfMuted, fontSize = 12.sp)
        }
        Text(
            "Сборка APK — GitHub Actions, не телефон. Несколько PAT: вкладка Cloud.",
            color = MfMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
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
