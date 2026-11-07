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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(vm: AppViewModel, library: Library) {
    val creds = vm.client?.creds
    val account = vm.account
    val languages by vm.storage.languages.collectAsState()
    val visible = vm.visibleLibrary ?: library
    val dateTime = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("nl", "NL"))
    val date = SimpleDateFormat("d MMMM yyyy", Locale("nl", "NL"))
    var confirmLogout by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 48.dp)) {
        item {
            Heading("Talen")
            Text(
                "Kies welke talen je wilt zien in Home, Films, Series, Live TV en bij het zoeken. " +
                    "Niets gekozen = alles tonen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(
                    text = "Alle talen",
                    selected = languages.isEmpty(),
                    onClick = { languages.forEach(vm::toggleLanguage) },
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
                "Zichtbaar: ${formatCount(visible.movies.size)} films · ${formatCount(visible.series.size)} series · " +
                    "${formatCount(visible.live.size)} zenders",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.textMuted,
            )
        }

        item {
            val format by vm.storage.liveFormat.collectAsState()
            Heading("Afspelen")
            Text(
                "Streamformaat voor live-tv. TS start bij de meeste IPTV-servers sneller; HLS is soms stabieler. " +
                    "Werkt het ene niet, dan probeert de app automatisch het andere.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Automatisch", "ts" to "TS", "m3u8" to "HLS (m3u8)").forEach { (value, label) ->
                    ChipButton(text = label, selected = format == value, onClick = { vm.storage.setLiveFormat(value) })
                }
            }
        }

        item {
            Heading("Updates")
            UpdateSection(vm)
        }

        item {
            Heading("Account")
            InfoLine("Server", creds?.server ?: "-")
            InfoLine("Gebruiker", creds?.username ?: "-")
            if (account != null) {
                InfoLine("Status", account.status)
                InfoLine("Verloopt op", account.expiresAt?.let { date.format(Date(it * 1000)) } ?: "Onbeperkt")
                if (account.maxConnections != null) {
                    InfoLine("Verbindingen", "${account.activeConnections ?: 0} van ${account.maxConnections} in gebruik")
                }
            }
        }

        item {
            Heading("Bibliotheek")
            InfoLine("Totaal", "${formatCount(library.movies.size)} films · ${formatCount(library.series.size)} series · ${formatCount(library.live.size)} zenders")
            InfoLine("Bijgewerkt", if (vm.refreshing) "Bezig met verversen…" else dateTime.format(Date(library.updatedAt)))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton("Lijsten vernieuwen", Icons.Filled.Refresh, onClick = vm::refreshInBackground)
                ActionButton("Uitloggen", Icons.AutoMirrored.Filled.Logout, onClick = { confirmLogout = true })
            }
            if (confirmLogout) {
                ConfirmRemoval(
                    title = "Daarna moet je server, gebruikersnaam en wachtwoord opnieuw invullen.",
                    question = "Uitloggen?",
                    confirmLabel = "Uitloggen",
                    confirmIcon = Icons.AutoMirrored.Filled.Logout,
                    onConfirm = { confirmLogout = false; vm.logout() },
                    onDismiss = { confirmLogout = false },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Lijsten worden automatisch elke 12 uur ververst. Tijdens het kijken: links/rechts spoelt 10 s terug of 30 s vooruit, " +
                    "bij live-tv zap je met omhoog/omlaag door de zenders van de categorie.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
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
