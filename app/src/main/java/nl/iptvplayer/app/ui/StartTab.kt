package nl.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val REMOVE_HINT = "Houd OK ingedrukt om te verwijderen"

/** Iets dat de gebruiker uit een lijst wil halen; wordt eerst bevestigd. */
internal class Removal(val title: String, val remove: () -> Unit)

/** Home: recent bekeken zenders, verder kijken (series en films), favorieten en nieuw toegevoegd. */
@Composable
fun StartTab(vm: AppViewModel, library: Library) {
    val context = LocalContext.current
    val progress by vm.storage.progress.collectAsState()
    val recentKeys by vm.storage.recentLive.collectAsState()
    val favorites by vm.storage.favorites.collectAsState()
    val restorer = rememberFocusRestorer()
    var removal by remember { mutableStateOf<Removal?>(null) }

    // Recent bekeken en verder kijken komen uit de volledige bibliotheek, los van het taalfilter.
    val all = vm.library ?: library
    val recentLive = remember(recentKeys, all) { recentKeys.mapNotNull { all.byKey[it] } }
    val seriesProgress = progress.filter { it.seriesId != null }
    val movieProgress = progress.filter { it.seriesId == null }
    val newMovies = library.newestMovies
    val newSeries = library.newestSeries

    // Na het kijken staat de laatst bekeken zender vooraan; de focus gaat mee in plaats van op de oude plek te blijven.
    val recentRow = rememberLazyListState()
    val firstRecentFocus = remember { FocusRequester() }
    val firstRecent = recentLive.firstOrNull()?.key
    var seenFirstRecent by remember { mutableStateOf(firstRecent) }
    LaunchedEffect(firstRecent) {
        if (firstRecent == seenFirstRecent) return@LaunchedEffect
        seenFirstRecent = firstRecent
        if (firstRecent == null) return@LaunchedEffect
        recentRow.scrollToItem(0)
        firstRecentFocus.requestSafely()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        // 1. Live TV
        if (recentLive.isNotEmpty()) groupHeader("Live TV")
        section("Recent bekeken", recentLive, { it.key }, hint = REMOVE_HINT, id = "live-recent", subRow = true, rowState = recentRow) { i, ch ->
            ChannelCard(
                ch,
                onClick = { vm.playLive(context, recentLive, i) },
                onLongClick = { removal = Removal(ch.title) { vm.storage.removeRecentLive(ch.key) } },
                isFavorite = ch.key in favorites,
                modifier = Modifier.width(280.dp)
                    .then(if (i == 0) Modifier.focusRequester(firstRecentFocus) else Modifier)
                    .restoreFocus(restorer, "rl:${ch.key}"),
            )
        }

        // 2. Series
        groupHeader("Series")
        section("Kijk verder", seriesProgress, { it.key }, hint = REMOVE_HINT, id = "series-continue", subRow = true) { _, p ->
            ProgressCard(
                p,
                onClick = { vm.resume(context, p) },
                onLongClick = { removal = Removal(p.title) { vm.storage.removeProgress(p.key) } },
                modifier = Modifier.restoreFocus(restorer, "sp:${p.key}"),
            )
        }
        expandingSection(vm, "Nieuwe series", "series-new", newSeries, restorer)

        // 3. Films
        groupHeader("Films")
        section("Kijk verder", movieProgress, { it.key }, hint = REMOVE_HINT, id = "movies-continue", subRow = true) { _, p ->
            ProgressCard(
                p,
                onClick = { vm.resume(context, p) },
                onLongClick = { removal = Removal(p.title) { vm.storage.removeProgress(p.key) } },
                modifier = Modifier.restoreFocus(restorer, "mp:${p.key}"),
            )
        }
        expandingSection(vm, "Nieuwe films", "movies-new", newMovies, restorer)

        item(key = "status") { StatusLine(vm) }
    }

    removal?.let { r ->
        ConfirmRemoval(r.title, onConfirm = { r.remove(); removal = null }, onDismiss = { removal = null })
    }
}

@Composable
internal fun ConfirmRemoval(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    question: String = "Verwijderen uit deze lijst?",
    confirmLabel: String = "Verwijderen",
    confirmIcon: ImageVector = Icons.Outlined.Delete,
) {
    val removeButton = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .background(Palette.surface, RoundedCornerShape(24.dp))
                .padding(32.dp),
        ) {
            Text(question, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.textMuted)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton(confirmLabel, confirmIcon, onClick = onConfirm, modifier = Modifier.focusRequester(removeButton))
                ActionButton("Annuleren", null, onClick = onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { removeButton.requestSafely() }
}

@Composable
private fun StatusLine(vm: AppViewModel) {
    val account = vm.account
    val expires = account?.expiresAt?.let {
        " · abonnement tot " + SimpleDateFormat("d MMMM yyyy", Locale("nl", "NL")).format(Date(it * 1000))
    } ?: ""
    Row(Modifier.padding(start = 56.dp, top = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (account != null) Palette.online else Palette.textMuted, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            (if (account != null) "Verbonden" else "Offline – opgeslagen lijsten") + expires,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.textMuted,
        )
    }
}

@Composable
fun FavoritesTab(vm: AppViewModel, library: Library) {
    val context = LocalContext.current
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val restorer = rememberFocusRestorer()
    val items = remember(favorites, library) { favorites.mapNotNull { library.byKey[it] } }
    val live = items.filter { it.kind == Kind.LIVE }
    val movies = items.filter { it.kind == Kind.MOVIE }
    val series = items.filter { it.kind == Kind.SERIES }

    if (items.isEmpty()) {
        CenteredMessage(
            "Nog geen favorieten",
            "Houd OK ingedrukt op een film, serie of zender om hem hier te bewaren.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        section("Zenders", live, { it.key }) { i, ch ->
            ChannelCard(
                ch, onClick = { vm.playLive(context, live, i) }, isFavorite = true,
                onLongClick = { vm.storage.toggleFavorite(ch.key) },
                modifier = Modifier.width(280.dp).restoreFocus(restorer, ch.key),
            )
        }
        section("Films", movies, { it.key }) { _, m ->
            PosterCard(
                m, onClick = { vm.open(m, context) }, isFavorite = true, isWatched = m.key in watched,
                onLongClick = { vm.storage.toggleFavorite(m.key) },
                modifier = Modifier.restoreFocus(restorer, m.key),
            )
        }
        section("Series", series, { it.key }) { _, s ->
            PosterCard(
                s, onClick = { vm.open(s, context) }, isFavorite = true,
                onLongClick = { vm.storage.toggleFavorite(s.key) },
                modifier = Modifier.restoreFocus(restorer, s.key),
            )
        }
    }
}
