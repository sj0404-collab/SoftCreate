package com.mobileforge.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MfHero(
            "GitHub Cloud",
            "Несколько PAT/аккаунтов. Выбор или создание репо. Сборка APK — только GitHub Actions runner, телефон проверяет preview.",
        )
        Text("Аккаунты", color = MfCyan, fontSize = 13.sp)
        vm.ghAccounts.forEach { acc ->
            MfCard {
                Text("${acc.login}  ·  ${acc.label}", color = if (acc.id == vm.github.activeId) MfPurple else MfText)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MfButton("Сделать активным", primary = acc.id == vm.github.activeId) { vm.selectGithubAccount(acc.id) }
                    MfButton("Удалить", danger = true) { vm.removeGithubAccount(acc.id) }
                }
            }
        }
        MfField(vm.ghLabel, { vm.ghLabel = it }, "Метка аккаунта")
        MfField(vm.ghTokenInput, { vm.ghTokenInput = it }, "GitHub PAT (repo + workflow)", password = true)
        MfButton("＋ Добавить аккаунт", primary = true, enabled = !vm.cloudBusy) { vm.addGithubAccount() }

        Text("Репозиторий", color = MfCyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Привязан: ${vm.boundRepo.ifBlank { "нет" }}", color = MfMuted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Обновить список", enabled = !vm.cloudBusy) { vm.loadRepos() }
        }
        vm.ghRepos.take(30).forEach { repo ->
            MfCard {
                Text(repo.fullName + if (repo.privateRepo) "  (private)" else "", color = MfText, fontSize = 13.sp)
                MfButton("Выбрать", primary = repo.fullName == vm.boundRepo) { vm.bindRepo(repo.fullName) }
            }
        }
        MfField(vm.newRepoName, { vm.newRepoName = it }, "Новый репозиторий")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton(if (vm.newRepoPrivate) "private" else "public") { vm.newRepoPrivate = !vm.newRepoPrivate }
            MfButton("Создать репо", primary = true, enabled = !vm.cloudBusy) { vm.createRepo() }
        }

        Text("Синхронизация и runner", color = MfCyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MfButton("Push проекта", primary = true, enabled = !vm.cloudBusy) { vm.pushProjectToGithub() }
            MfButton("Собрать на runner", enabled = !vm.cloudBusy) { vm.triggerCloudBuild() }
            MfButton("Статус runs") { vm.loadRuns() }
        }
        vm.ghRuns.forEach { run ->
            Text("${run.name}: ${run.status}/${run.conclusion}", color = MfMuted, fontSize = 12.sp)
        }
    }
}
