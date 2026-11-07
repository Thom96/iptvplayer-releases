package nl.iptvplayer.app.ui

import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.CatchupEntry
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val nl = Locale("nl", "NL")
private val timeFmt = SimpleDateFormat("HH:mm", nl)
private val dayFmt = SimpleDateFormat("EEE d MMM", nl)

/** Dagnummer (lokale tijd) om programma's per dag te groeperen. */
private fun dayKey(ms: Long): Int = Calendar.getInstance().run {
    timeInMillis = ms
    get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(ms: Long, now: Long): String = when (dayKey(ms)) {
    dayKey(now) -> "Vandaag"
    dayKey(now - 24 * 3_600_000L) -> "Gisteren"
    else -> dayFmt.format(Date(ms)).replaceFirstChar { it.uppercase() }
}

/**
 * Terugkijken: links zenders met archief, rechts per dag de programma's die al zijn uitgezonden.
 */
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CatchupTab(vm: AppViewModel, library: Library) {
    val context = LocalContext.current
    val channels = remember(library) {
        library.live.filter { it.archiveDays > 0 }.sortedBy { if (it.num > 0) it.num else Int.MAX_VALUE }
    }
    if (channels.isEmpty()) {
        CenteredMessage(
            "Geen zenders met terugkijken",
            "Je provider biedt (in de gekozen talen) geen zenders aan waarvan je programma's kunt terugkijken.",
        )
        return
    }

    var focusedKey by rememberSaveable { mutableStateOf(channels.first().key) }
    var selectedKey by rememberSaveable { mutableStateOf(channels.first().key) }
    // Pas de gids laden als je even op een zender blijft staan.
    LaunchedEffect(focusedKey) {
        delay(400)
        selectedKey = focusedKey
    }
    val channel = channels.firstOrNull { it.key == selectedKey } ?: channels.first()

    val channelFocus = rememberListFocusMemory()
    Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 8.dp)) {
        LazyColumn(
            Modifier.width(320.dp).fillMaxHeight().enterFocusAt(channelFocus),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(channels, key = { it.key }) { ch ->
                ArchiveChannelRow(
                    ch, selected = ch.key == channel.key, onFocused = { focusedKey = ch.key },
                    modifier = Modifier.listFocusTarget(channelFocus, ch.key == focusedKey),
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        ArchiveProgrammes(vm, channel, onPlay = { vm.playCatchup(context, channel, it) })
    }
}

@Composable
private fun ArchiveChannelRow(channel: MediaItem, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onFocused,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tapToClick(onFocused),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.surface else Color.Transparent,
            contentColor = Palette.text,
            focusedContainerColor = Palette.surfaceFocused,
            focusedContentColor = Palette.text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(12.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                AsyncImage(channel.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${channel.archiveDays} ${if (channel.archiveDays == 1) "dag" else "dagen"} terug",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.7f),
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ArchiveProgrammes(vm: AppViewModel, channel: MediaItem, onPlay: (CatchupEntry) -> Unit) {
    LaunchedEffect(channel.id) { vm.loadArchive(channel.id) }
    val now = remember(channel.id) { System.currentTimeMillis() }
    val entries = vm.archive[channel.id]

    Column(Modifier.fillMaxSize()) {
        Text(channel.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (entries == null) {
            Text("Archief laden…", color = Palette.textMuted)
            return
        }
        // Alleen wat al begonnen is, binnen de archiefperiode; per dag, nieuwste dag eerst.
        val oldest = now - channel.archiveDays * 24 * 3_600_000L
        // distinctBy: sommige gidsen bevatten dubbele programma's; dubbele keys laten de lijst crashen.
        val past = entries.filter { it.start in oldest until now }.distinctBy { it.start }
        if (past.isEmpty()) {
            Text("Geen programma's in het archief van deze zender.", color = Palette.textMuted)
            return
        }
        val days = past.groupBy { dayKey(it.start) }.toSortedMap(compareByDescending { it })
        var selectedDay by remember(channel.id) { mutableStateOf(days.firstKey()) }
        val dayEntries = days[selectedDay].orEmpty().sortedByDescending { it.start }

        val dayFocus = rememberListFocusMemory()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.enterFocusAt(dayFocus)) {
            items(days.keys.toList(), key = { it }) { key ->
                val label = dayLabel(days.getValue(key).first().start, now)
                ChipButton(
                    text = label,
                    selected = key == selectedDay,
                    onClick = { selectedDay = key },
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) selectedDay = key }
                        .listFocusTarget(dayFocus, key == selectedDay),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val listState = rememberLazyListState()
        LaunchedEffect(selectedDay) { listState.scrollToItem(0) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(dayEntries, key = { it.start }) { e -> ProgrammeRow(e, now, onPlay) }
        }
    }
}

@Composable
private fun ProgrammeRow(entry: CatchupEntry, now: Long, onPlay: (CatchupEntry) -> Unit) {
    val live = now in entry.start until entry.end
    val playable = entry.hasArchive || entry.end <= now
    val click = { if (playable) onPlay(entry) }
    Surface(
        onClick = click,
        modifier = Modifier.fillMaxWidth().tapToClick(click),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.surface.copy(alpha = 0.6f),
            contentColor = Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
    ) {
        Row(Modifier.padding(14.dp).alpha(if (playable) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(110.dp)) {
                Text(timeFmt.format(Date(entry.start)), style = MaterialTheme.typography.titleMedium)
                Text(
                    "tot ${timeFmt.format(Date(entry.end))}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.7f),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) {
                        Text(
                            "NU",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.background,
                            modifier = Modifier.background(Palette.accent, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                entry.description?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.7f))
                }
            }
            if (playable) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.PlayArrow, "Terugkijken", Modifier.size(24.dp))
            }
        }
    }
}
