package nl.iptvplayer.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.iptvplayer.app.data.Progress
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.Screen
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.TextMatch

private const val ROW_SIZE = 20

/** Eén rij in het overzicht: een categorie met de nieuwste titels. */
private class CategoryRow(val id: String, val title: String, val total: Int, val items: List<MediaItem>)

/**
 * Films of series zoals bij Netflix: per categorie een horizontale rij, met "Alles bekijken"
 * aan het eind voor het volledige raster met sorteren.
 */
@Composable
fun CategoryRowsTab(vm: AppViewModel, library: Library, kind: Kind) {
    val context = LocalContext.current
    val restorer = rememberFocusRestorer()
    val progress by vm.storage.progress.collectAsState()
    var removal by remember { mutableStateOf<Removal?>(null) }
    // Verder kijken: bij Series de begonnen series, bij Films de begonnen films.
    val continueItems = progress.filter { (it.seriesId != null) == (kind == Kind.SERIES) }

    // Groeperen en sorteren van alle titels op de achtergrond, en per bibliotheek onthouden zodat
    // wisselen van tabblad direct gaat.
    val rows by produceState(rowCache[library]?.get(kind), library, kind) {
        val cached = rowCache[library]?.get(kind)
        value = cached ?: withContext(Dispatchers.Default) { buildRows(library, kind) }
            .also { rowCache.getOrPut(library) { HashMap() }[kind] = it }
    }
    val shown = rows ?: return

    if (shown.size <= 1 && shown.firstOrNull()?.items.isNullOrEmpty()) {
        CenteredMessage("Niets gevonden", "Er zijn geen titels in de gekozen talen. Pas het taalfilter aan bij Instellingen.")
        return
    }
    CategoryRowsList(vm, kind, shown, continueItems, restorer, context) { removal = it }

    removal?.let { r ->
        ConfirmRemoval(r.title, onConfirm = { r.remove(); removal = null }, onDismiss = { removal = null })
    }
}

/** Per bibliotheek (taalfilter) de berekende rijen; zwak vastgehouden zodat oude bibliotheken opgeruimd worden. */
private val rowCache = java.util.WeakHashMap<Library, HashMap<Kind, List<CategoryRow>>>()

private fun buildRows(library: Library, kind: Kind): List<CategoryRow> {
        val all = library.items(kind)
        val byCategory = all.groupBy { it.categoryId }
        return buildList {
            add(CategoryRow(ALL_CATEGORIES, "Nieuw toegevoegd", all.size, all.sortedByDescending { it.added }.take(ROW_SIZE)))
            // Genre-rijen: alleen series hebben het genre al in de lijst van de provider.
            if (kind == Kind.SERIES) addAll(genreRows(all))
            library.categories(kind).forEach { cat ->
                val items = byCategory[cat.id].orEmpty()
                if (items.isNotEmpty()) {
                    add(CategoryRow(cat.id, prettyCategory(cat.name), items.size, items.sortedByDescending { it.added }.take(ROW_SIZE)))
                }
            }
        }
}

@Composable
private fun CategoryRowsList(
    vm: AppViewModel,
    kind: Kind,
    rows: List<CategoryRow>,
    continueItems: List<Progress>,
    restorer: FocusRestorer,
    context: Context,
    onRemove: (Removal) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        section("Verder kijken", continueItems, { it.key }, hint = REMOVE_HINT, id = "continue") { _, p ->
            ProgressCard(
                p,
                onClick = { vm.resume(context, p) },
                onLongClick = { onRemove(Removal(p.title) { vm.storage.removeProgress(p.key) }) },
                modifier = Modifier.restoreFocus(restorer, "cw:${p.key}"),
            )
        }
        rows.forEach { row ->
            item(key = "title:${row.id}") {
                Row(
                    Modifier.padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(row.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Text(formatCount(row.total), style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            item(key = "row:${row.id}") {
                ExpandingPosterRow(
                    vm, row.items, restorer, row.id,
                    trailing = if (row.total > row.items.size) {
                        {
                            SeeAllCard(
                                total = row.total,
                                onClick = { vm.nav.add(Screen.Browse(kind, row.id)) },
                                modifier = Modifier.restoreFocus(restorer, "${row.id}:more"),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** Genrenamen van de provider (Engels, van TMDB) met een Nederlandse rijtitel. */
private val GENRE_TITLES = linkedMapOf(
    "comedy" to "Komedie",
    "drama" to "Drama",
    "crime" to "Misdaad",
    "action & adventure" to "Actie & avontuur",
    "sci-fi & fantasy" to "Sci-fi & fantasy",
    "mystery" to "Mysterie",
    "documentary" to "Documentaires",
    "reality" to "Reality",
    "animation" to "Animatie",
    "kids" to "Voor kinderen",
    "family" to "Familie",
    "war & politics" to "Oorlog & politiek",
    "romance" to "Romantiek",
    "thriller" to "Thrillers",
    "western" to "Westerns",
    "history" to "Geschiedenis",
    "talk" to "Talkshows",
)

/** Prefix voor genre-"categorieën" in het raster (zie BrowseTab). */
const val GENRE_PREFIX = "genre:"

fun genresOf(item: MediaItem): List<String> =
    item.genre?.split(',', '/')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

private fun genreRows(series: List<MediaItem>): List<CategoryRow> {
    val byGenre = HashMap<String, MutableList<MediaItem>>()
    series.forEach { s -> genresOf(s).forEach { g -> byGenre.getOrPut(g) { ArrayList() } += s } }
    return GENRE_TITLES.mapNotNull { (genre, title) ->
        val items = byGenre[genre] ?: return@mapNotNull null
        // Kleine genres overslaan; alleen titels met een poster tonen.
        if (items.size < 10) return@mapNotNull null
        CategoryRow(
            GENRE_PREFIX + genre, title, items.size,
            items.filter { it.image != null }.sortedByDescending { it.added }.take(ROW_SIZE),
        )
    }
}

@Composable
private fun SeeAllCard(total: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.width(128.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.tapToClick(onClick).fillMaxWidth().aspectRatio(2f / 3f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.surface,
                contentColor = Palette.text,
                focusedContainerColor = Palette.text,
                focusedContentColor = Palette.background,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(32.dp))
                Spacer(Modifier.height(10.dp))
                Text("Alles bekijken", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                Text(formatCount(total), style = MaterialTheme.typography.bodySmall, color = Color.Unspecified)
            }
        }
    }
}

/** "┃NL┃ AMAZON PRIME VIDEO" -> "Amazon Prime Video"; korte afkortingen (HBO, NPO, 4K) blijven hoofdletters. */
private fun prettyCategory(name: String): String =
    TextMatch.displayTitle(name).split(' ').joinToString(" ") { w ->
        if (w.length <= 3 || w.any { it.isDigit() }) w else w.lowercase().replaceFirstChar { it.uppercase() }
    }
