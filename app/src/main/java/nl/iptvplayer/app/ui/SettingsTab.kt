package nl.iptvplayer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.LicenseStatus
import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(vm: AppViewModel, library: Library) {
    val creds = vm.client?.creds
    val account = vm.account
    val languages by vm.storage.languages.collectAsState()
    val visible = vm.visibleLibrary ?: library
    val dateTime = SimpleDateFormat("d MMM yyyy, HH:mm", appLocale)
    val date = SimpleDateFormat("d MMMM yyyy", appLocale)
    var confirmLogout by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 48.dp)) {
        item {
            Heading(S.appLanguage)
            Text(S.appLanguageHint, style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted)
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    ChipButton(text = lang.nativeName, selected = lang == I18n.language, onClick = { vm.setAppLanguage(lang) })
                }
            }
        }

        item {
            Heading(S.contentLanguages)
            Text(
                S.contentLanguagesHint,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(
                    text = S.allLanguages,
                    selected = languages.isEmpty(),
                    onClick = vm::showAllLanguages,
                )
                library.languageCounts.forEach { (tag, count) ->
                    ChipButton(
                        text = tag,
                        selected = tag in languages,
                        onClick = { vm.toggleLanguage(tag) },
                        trailing = formatCount(count),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                S.visible(S.libraryCounts(formatCount(visible.movies.size), formatCount(visible.series.size), formatCount(visible.live.size))),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.textMuted,
            )
        }

        item {
            val format by vm.storage.liveFormat.collectAsState()
            Heading(S.playback)
            Text(
                S.playbackHint,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to S.formatAuto, "ts" to "TS", "m3u8" to "HLS (m3u8)").forEach { (value, label) ->
                    ChipButton(text = label, selected = format == value, onClick = { vm.storage.setLiveFormat(value) })
                }
            }
        }

        item {
            Heading(S.updates)
            UpdateSection(vm)
        }

        item {
            Heading(S.account)
            InfoLine(S.server, creds?.server ?: "-")
            InfoLine(S.user, creds?.username ?: "-")
            if (account != null) {
                InfoLine(S.status, account.status)
                InfoLine(S.expiresOn, account.expiresAt?.let { date.format(Date(it * 1000)) } ?: S.unlimited)
                if (account.maxConnections != null) {
                    InfoLine(S.connections, S.connectionsInUse(account.activeConnections ?: 0, account.maxConnections))
                }
            }
        }

        vm.license?.let { license ->
            item {
                Heading(S.licenseSection)
                InfoLine(S.deviceCode, license.code)
                InfoLine(
                    S.status,
                    when (license.status) {
                        LicenseStatus.TRIAL -> S.licenseTrial
                        LicenseStatus.ACTIVE -> S.licenseActive
                        LicenseStatus.FREE -> S.licenseFree
                        LicenseStatus.EXPIRED -> S.licenseExpired
                        LicenseStatus.BLOCKED -> S.licenseBlocked
                    },
                )
                InfoLine(S.validUntil, license.validUntil?.let { date.format(Date(it)) } ?: S.noEndDate)
            }
        }

        item {
            Heading(S.library)
            InfoLine(S.total, S.libraryCounts(formatCount(library.movies.size), formatCount(library.series.size), formatCount(library.live.size)))
            InfoLine(S.updated, if (vm.refreshing) S.refreshing else dateTime.format(Date(library.updatedAt)))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton(S.refreshLists, Icons.Filled.Refresh, onClick = vm::refreshInBackground)
                ActionButton(S.logout, Icons.AutoMirrored.Filled.Logout, onClick = { confirmLogout = true })
            }
            if (confirmLogout) {
                ConfirmRemoval(
                    title = S.logoutWarning,
                    question = S.logoutQuestion,
                    confirmLabel = S.logout,
                    confirmIcon = Icons.AutoMirrored.Filled.Logout,
                    onConfirm = { confirmLogout = false; vm.logout() },
                    onDismiss = { confirmLogout = false },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                S.settingsFooter,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
            Spacer(Modifier.height(10.dp))
            TmdbBadge(text = S.tmdbNotice)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Palette.textMuted, modifier = Modifier.width(180.dp))
        Text(value)
    }
}
