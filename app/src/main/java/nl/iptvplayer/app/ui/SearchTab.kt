package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.SearchEngine
import nl.iptvplayer.app.data.SearchResults

@Composable
fun SearchTab(vm: AppViewModel, library: Library) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val restorer = rememberFocusRestorer()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(query, library) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        // Na terugkeren direct resultaten tonen; tijdens het typen even wachten.
        if (!first) delay(250)
        first = false
        results = withContext(Dispatchers.Default) { SearchEngine.search(library, query) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)) {
        item(key = "field") {
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = S.searchPlaceholder,
                imeAction = ImeAction.Search,
                onImeAction = {
                    keyboard?.hide()
                    focus.moveFocus(FocusDirection.Down)
                },
                leading = Icons.Filled.Search,
                modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(),
            )
        }
        val r = results
        when {
            query.isBlank() -> item(key = "hint") {
                Hint(
                    S.searchHint,
                )
            }
            r == null -> item(key = "busy") { Hint(S.searching) }
            r.isEmpty -> item(key = "none") { Hint(S.nothingFoundFor(r.query)) }
            else -> {
                section("${S.tabMovies} ${count(r.movies)}", r.movies, { it.key }) { _, m -> Poster(vm, m, favorites, watched, restorer, context) }
                section("${S.tabSeries} ${count(r.series)}", r.series, { it.key }) { _, s -> Poster(vm, s, favorites, watched, restorer, context) }
                section("${S.channels} ${count(r.live)}", r.live, { it.key }) { i, ch ->
                    ChannelCard(
                        ch,
                        onClick = { vm.playLive(context, r.live, i) },
                        isFavorite = ch.key in favorites,
                        onLongClick = { vm.storage.toggleFavorite(ch.key) },
                        modifier = Modifier.width(280.dp).restoreFocus(restorer, ch.key),
                    )
                }
            }
        }
    }
}

private fun count(list: List<MediaItem>) = if (list.size >= 150) "(150+)" else "(${list.size})"

@Composable
private fun Poster(
    vm: AppViewModel,
    item: MediaItem,
    favorites: Set<String>,
    watched: Set<String>,
    restorer: FocusRestorer,
    context: android.content.Context,
) {
    PosterCard(
        item,
        onClick = { vm.open(item, context) },
        isFavorite = item.key in favorites,
        isWatched = item.key in watched,
        onLongClick = { vm.storage.toggleFavorite(item.key) },
        modifier = Modifier.restoreFocus(restorer, item.key),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Palette.textMuted,
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp),
    )
}
