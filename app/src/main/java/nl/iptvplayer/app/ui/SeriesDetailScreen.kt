package nl.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.rememberLazyListState
import nl.iptvplayer.app.data.TextMatch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Episode
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.Progress
import nl.iptvplayer.app.data.SeriesInfo

@Composable
fun SeriesDetailScreen(vm: AppViewModel, item: MediaItem) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf(false) }
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val progressList by vm.storage.progress.collectAsState()
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(item.id) {
        try {
            info = vm.client?.seriesInfo(item.id)
        } catch (e: Exception) {
            error = true
        }
    }

    val seasons = info?.seasons.orEmpty()
    val allEpisodes = remember(info) { seasons.flatMap { it.episodes } }

    // Wat is de volgende aflevering? Een half bekeken aflevering, anders de eerste na de laatst geziene.
    val nextUp: Pair<Episode, Progress?>? = remember(allEpisodes, progressList, watched) {
        val p = progressList.firstOrNull { it.seriesId == item.id }
        val resumeEp = p?.let { pp -> allEpisodes.firstOrNull { it.key == pp.key } }
        when {
            resumeEp != null -> resumeEp to p
            allEpisodes.isEmpty() -> null
            else -> {
                val last = allEpisodes.indexOfLast { it.key in watched }
                (allEpisodes.getOrNull(last + 1) ?: allEpisodes.first()) to null
            }
        }
    }

    var seasonIndex by rememberSaveable { mutableIntStateOf(-1) }
    val currentSeason = when {
        seasons.isEmpty() -> null
        seasonIndex in seasons.indices -> seasons[seasonIndex]
        else -> seasons.firstOrNull { s -> s.number == nextUp?.first?.season } ?: seasons.first()
    }

    LaunchedEffect(nextUp != null) { if (nextUp != null) playFocus.requestSafely() }

    val i = info
    DetailBackdrop(i?.backdrop ?: i?.poster ?: item.image)

    val seasonFocus = rememberListFocusMemory()
    val episodeList = rememberLazyListState()
    val episodes = currentSeason?.episodes.orEmpty()
    // Afleveringenlijst openen bij de aflevering waar je gebleven bent.
    LaunchedEffect(currentSeason, nextUp?.first) {
        val idx = episodes.indexOfFirst { it.key == nextUp?.first?.key }
        if (idx >= 0) episodeList.scrollToItem(idx)
    }

    // De bovenkant staat vast; alleen de afleveringen scrollen. Zo valt de bovenkant nooit half weg.
    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        run {
            Row(Modifier.padding(horizontal = 64.dp)) {
                AsyncImage(
                    model = i?.poster ?: item.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(132.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Palette.surface),
                )
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    val meta = listOfNotNull(
                        i?.releaseDate?.take(4) ?: item.year?.toString(),
                        seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 seizoen" else "$it seizoenen" },
                        i?.genre,
                        (i?.rating ?: item.rating.takeIf { it > 0 })?.let { "★ %.1f".format(it) },
                    )
                    if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), color = Palette.textMuted)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            i?.plot != null -> i.plot
                            error -> "Kon de serie-informatie niet laden."
                            i == null -> "Afleveringen laden…"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        nextUp?.let { (ep, p) ->
                            val label = (if (p != null) "Verder: " else "Afspelen: ") + "S${ep.season} E${ep.number}"
                            ActionButton(
                                label, Icons.Filled.PlayArrow,
                                onClick = { vm.playEpisodes(context, item, allEpisodes, allEpisodes.indexOf(ep)) },
                                modifier = Modifier.focusRequester(playFocus),
                            )
                        }
                        val fav = item.key in favorites
                        ActionButton(
                            if (fav) "In favorieten" else "Favoriet",
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            onClick = { vm.storage.toggleFavorite(item.key) },
                        )
                    }
                }
            }
        }

        if (seasons.size > 1) {
            // Binnenkomen in deze rij = op het geselecteerde seizoen; focus op een seizoen = dat seizoen tonen.
            LazyRow(
                modifier = Modifier.enterFocusAt(seasonFocus),
                contentPadding = PaddingValues(horizontal = 64.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(seasons, key = { _, s -> s.number }) { index, s ->
                    ChipButton(
                        text = s.name,
                        selected = s == currentSeason,
                        onClick = { seasonIndex = index },
                        trailing = s.episodes.size.toString(),
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) seasonIndex = index }
                            .listFocusTarget(seasonFocus, s == currentSeason),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            state = episodeList,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            items(episodes, key = { it.id }) { ep ->
                val p = progressList.firstOrNull { it.key == ep.key }
                EpisodeRow(
                    ep,
                    fallbackImage = item.image,
                    progress = p,
                    isWatched = ep.key in watched,
                    onClick = { vm.playEpisodes(context, item, allEpisodes, allEpisodes.indexOf(ep)) },
                )
            }
        }
    }
}


@Composable
private fun EpisodeRow(ep: Episode, fallbackImage: String?, progress: Progress?, isWatched: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 56.dp, vertical = 4.dp).fillMaxWidth().tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.surface.copy(alpha = 0.6f),
            focusedContainerColor = Palette.surfaceFocused,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(200.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Palette.background)) {
                AsyncImage(
                    model = ep.image ?: fallbackImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (progress != null) ProgressBar(progress.fraction, Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("${ep.number}. ${TextMatch.episodeTitle(ep.title)}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ep.plot?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                ep.durationSecs?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
                }
            }
            if (isWatched) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.CheckCircle, "Gezien", Modifier.size(22.dp), tint = Palette.accent)
            }
        }
    }
}
