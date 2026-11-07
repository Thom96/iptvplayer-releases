package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.MovieInfo

/** Film-detail in dezelfde opbouw als serie-detail: beeld rechts, titel, gegevens en knoppen links. */
@Composable
fun MovieDetailScreen(vm: AppViewModel, item: MediaItem) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    val favorites by vm.storage.favorites.collectAsState()
    val progressList by vm.storage.progress.collectAsState()
    val progress = progressList.firstOrNull { it.key == item.key }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(item.id) {
        info = runCatching { vm.client?.movieInfo(item.id) }.getOrNull()
        loading = false
    }
    LaunchedEffect(Unit) { playFocus.requestSafely() }

    val i = info
    DetailBackdrop(i?.backdrop ?: i?.poster ?: item.image)

    // Een film heeft geen rijen eronder: de inhoud staat verticaal in het midden.
    Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp), verticalArrangement = Arrangement.Center) {
        DetailHeader(
            title = item.title,
            rating = i?.rating ?: item.rating.takeIf { it > 0 },
            meta = listOfNotNull(
                i?.releaseDate?.take(4) ?: item.year?.toString(),
                i?.durationSecs?.let(::formatDuration),
                i?.genre,
            ),
            description = when {
                i?.plot != null -> i.plot
                loading -> S.loadingInfo
                else -> S.noDescription
            },
            descriptionLines = 5,
            extra = listOfNotNull(i?.director?.let { S.director(it) }, i?.cast?.let { S.cast(it) }),
        ) {
            val ext = i?.ext
            if (progress != null) {
                ActionButton(
                    S.resumeAt(formatTime(progress.positionMs)), Icons.Filled.PlayArrow,
                    onClick = { vm.playMovie(context, item, ext, fromStart = false) },
                    modifier = Modifier.focusRequester(playFocus),
                    primary = true,
                )
                ActionButton(S.fromStart, Icons.Filled.Replay, onClick = { vm.playMovie(context, item, ext, fromStart = true) })
            } else {
                ActionButton(
                    S.play, Icons.Filled.PlayArrow,
                    onClick = { vm.playMovie(context, item, ext, fromStart = true) },
                    modifier = Modifier.focusRequester(playFocus),
                    primary = true,
                )
            }
            val fav = item.key in favorites
            ActionButton(
                if (fav) S.inFavorites else S.favorite,
                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                onClick = { vm.storage.toggleFavorite(item.key) },
            )
        }
        if (progress != null && progress.durationMs > 0) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(progress.fraction, Modifier.widthIn(max = 280.dp))
                Text(
                    S.timeLeft(formatRemaining(progress)).replaceFirstChar { it.uppercase(appLocale) },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.textMuted,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
    }
}

/**
 * Bovenkant van een detailscherm uit het brandbook: titel in Young Serif, sterren in champagne gevolgd
 * door jaar, duur of seizoenen, een omschrijving en daaronder de knoppen.
 */
@Composable
internal fun DetailHeader(
    title: String,
    rating: Double?,
    meta: List<String>,
    description: String,
    descriptionLines: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    extra: List<String> = emptyList(),
    buttons: @Composable RowScope.() -> Unit,
) {
    Column(modifier.widthIn(max = 600.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rating != null) {
                Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = Palette.accent)
                Text(
                    "%.1f".format(appLocale, rating),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.accent,
                    modifier = Modifier.padding(start = 4.dp, end = if (meta.isEmpty()) 0.dp else 12.dp),
                )
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = Palette.textSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.titleSmall, color = Palette.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.textSoft,
            maxLines = descriptionLines,
            minLines = descriptionLines.coerceAtMost(3),
            overflow = TextOverflow.Ellipsis,
        )
        extra.forEach {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), content = buttons)
    }
}

/**
 * Achtergrond van een detailscherm: het beeld rechtsboven op volle sterkte, aflopend naar petrol aan de
 * linkerkant en onderin, zodat de tekst links en de rijen onderin altijd leesbaar zijn.
 */
@Composable
fun DetailBackdrop(image: String?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight(0.82f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Palette.background,
                    0.34f to Palette.background,
                    0.6f to Palette.background.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.4f to Color.Transparent, 0.82f to Palette.background),
            ),
        )
    }
}
