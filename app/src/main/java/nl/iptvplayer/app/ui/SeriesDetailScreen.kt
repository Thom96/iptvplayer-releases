package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Episode
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.Progress
import nl.iptvplayer.app.data.SeriesInfo
import nl.iptvplayer.app.data.withTmdb
import nl.iptvplayer.app.data.TextMatch

/**
 * Series detail as in the design: large image on the right, title and buttons on the left, seasons as chips and the
 * episodes as cards in one row. The description at the top shows the episode you are on.
 */
@Composable
fun SeriesDetailScreen(vm: AppViewModel, item: MediaItem) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf(false) }
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val progressList by vm.storage.progress.collectAsState()
    val playFocus = remember { FocusRequester() }

    var fromTmdb by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        val client = vm.client
        val provider = try {
            client?.seriesInfo(item.id)
        } catch (e: Exception) {
            error = true
            null
        }
        // Without the provider's details there are no episodes to play, so TMDB only improves a loaded series.
        if (provider == null) return@LaunchedEffect
        val meta = fetchTmdb(context, client, series = true, provider.tmdbId, item.title, item.year)
        info = provider.withTmdb(meta)
        fromTmdb = meta != null
    }

    val seasons = info?.seasons.orEmpty()
    val allEpisodes = remember(info) { seasons.flatMap { it.episodes } }

    // Which episode is next? A half-watched episode, otherwise the first after the last one watched.
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
    val episodeFocus = rememberListFocusMemory()
    val episodeList = rememberLazyListState()
    val episodes = currentSeason?.episodes.orEmpty()
    // Open the episode row at the episode where you left off.
    LaunchedEffect(currentSeason, nextUp?.first) {
        val idx = episodes.indexOfFirst { it.key == nextUp?.first?.key }
        if (idx >= 0) episodeList.scrollToItem(idx)
    }
    var focusedEpisode by remember { mutableStateOf<Episode?>(null) }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        val meta = listOfNotNull(
            i?.releaseDate?.take(4) ?: item.year?.toString(),
            seasons.size.takeIf { it > 0 }?.let { S.seasons(it) },
            i?.genre,
        )
        // If the focus is on an episode, the description tells about that episode.
        val ep = focusedEpisode
        DetailHeader(
            title = item.title,
            rating = i?.rating ?: item.rating.takeIf { it > 0 },
            meta = meta,
            subtitle = ep?.let { "${S.seasonEpisode(it.season, it.number)} · ${TextMatch.episodeTitle(it.title)}" },
            description = when {
                ep != null -> ep.plot.orEmpty()
                i?.plot != null -> i.plot
                error -> S.seriesInfoFailed
                i == null -> S.loadingEpisodes
                else -> ""
            },
            descriptionLines = 3,
            modifier = Modifier.padding(horizontal = 64.dp),
        ) {
            nextUp?.let { (next, p) ->
                val label = if (p != null) S.resumeEpisode(next.season, next.number) else S.playEpisode(next.season, next.number)
                ActionButton(
                    label, Icons.Filled.PlayArrow,
                    onClick = { vm.playEpisodes(context, item, allEpisodes, allEpisodes.indexOf(next)) },
                    modifier = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) focusedEpisode = null },
                    primary = true,
                )
            }
            val fav = item.key in favorites
            ActionButton(
                if (fav) S.inFavorites else S.favorite,
                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                onClick = { vm.storage.toggleFavorite(item.key) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedEpisode = null },
            )
        }

        if (seasons.size > 1) {
            // Entering this row = on the selected season; focus on a season = show that season.
            LazyRow(
                modifier = Modifier.enterFocusAt(seasonFocus),
                contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 18.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(seasons, key = { _, s -> s.number }) { index, s ->
                    ChipButton(
                        text = s.name,
                        selected = s == currentSeason,
                        onClick = { seasonIndex = index },
                        trailing = s.episodes.size.toString(),
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) { seasonIndex = index; focusedEpisode = null } }
                            .listFocusTarget(seasonFocus, s == currentSeason),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(18.dp))
        }

        // Entering the row = on the next episode (or the first).
        val target = episodes.firstOrNull { it.key == nextUp?.first?.key } ?: episodes.firstOrNull()
        LazyRow(
            state = episodeList,
            modifier = Modifier.fillMaxWidth().enterFocusAt(episodeFocus),
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(episodes, key = { it.id }) { e ->
                EpisodeCard(
                    e,
                    fallbackImage = item.image,
                    progress = progressList.firstOrNull { it.key == e.key },
                    isWatched = e.key in watched,
                    onClick = { vm.playEpisodes(context, item, allEpisodes, allEpisodes.indexOf(e)) },
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) focusedEpisode = e }
                        .listFocusTarget(episodeFocus, e == target),
                )
            }
        }
    }
    // Top right: the episode row fills the bottom of this screen.
    if (fromTmdb) {
        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
            TmdbBadge(Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Episode as a card: image with number and progress, title and duration or remaining time below. */
@Composable
private fun EpisodeCard(
    ep: Episode,
    fallbackImage: String?,
    progress: Progress?,
    isWatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(Modifier.width(232.dp)) {
        Card(
            onClick = onClick,
            modifier = modifier.tapToClick(onClick).fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = focusBorder(),
            colors = CardDefaults.colors(containerColor = Palette.surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ep.image ?: fallbackImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    S.episodeShort(ep.number),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.text,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Palette.badge, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                if (isWatched) {
                    Icon(
                        Icons.Filled.CheckCircle, S.watched,
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp).background(Palette.badge, CircleShape),
                        tint = Palette.online,
                    )
                }
                if (progress != null && progress.durationMs > 0) {
                    ProgressBar(progress.fraction, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            TextMatch.episodeTitle(ep.title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = when {
            progress != null && progress.durationMs > 0 -> S.timeLeft(formatRemaining(progress)).replaceFirstChar { it.uppercase(appLocale) }
            isWatched -> S.watched
            else -> ep.durationSecs?.let(::formatDuration)
        }
        if (meta != null) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, maxLines = 1)
        }
    }
}
