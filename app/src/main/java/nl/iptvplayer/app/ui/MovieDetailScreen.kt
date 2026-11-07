package nl.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.MovieInfo

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

    Row(
        Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = i?.poster ?: item.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(240.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Palette.surface),
        )
        Spacer(Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            val meta = listOfNotNull(
                (i?.releaseDate?.take(4) ?: item.year?.toString()),
                i?.durationSecs?.let(::formatDuration),
                i?.genre,
                (i?.rating ?: item.rating.takeIf { it > 0 })?.let { "★ %.1f".format(it) },
            )
            if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), color = Palette.textMuted)
            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    i?.plot != null -> i.plot
                    loading -> "Informatie laden…"
                    else -> "Geen beschrijving beschikbaar."
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
            )
            i?.director?.let {
                Spacer(Modifier.height(12.dp))
                Text("Regie: $it", color = Palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            i?.cast?.let {
                Text("Met: $it", color = Palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val ext = i?.ext
                if (progress != null) {
                    ActionButton(
                        "Verder kijken (${formatTime(progress.positionMs)})", Icons.Filled.PlayArrow,
                        onClick = { vm.playMovie(context, item, ext, fromStart = false) },
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    ActionButton("Vanaf begin", Icons.Filled.Replay, onClick = { vm.playMovie(context, item, ext, fromStart = true) })
                } else {
                    ActionButton(
                        "Afspelen", Icons.Filled.PlayArrow,
                        onClick = { vm.playMovie(context, item, ext, fromStart = true) },
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

/** Achtergrondafbeelding met verloop zodat tekst leesbaar blijft. */
@Composable
fun DetailBackdrop(image: String?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Palette.background,
                    0.55f to Palette.background.copy(alpha = 0.8f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Palette.background),
            ),
        )
    }
}
