package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.TextMatch

const val ALL_CATEGORIES = "__all__"
private const val ALL = ALL_CATEGORIES

enum class SortOrder {
    SERVER, NEWEST, AZ, RATING, YEAR;

    val label: String get() = when (this) {
        SERVER -> S.sortDefault
        NEWEST -> S.sortNewest
        AZ -> S.sortAz
        RATING -> S.sortRating
        YEAR -> S.sortYear
    }
}

private fun sortOptions(kind: Kind) =
    if (kind == Kind.LIVE) listOf(SortOrder.SERVER, SortOrder.AZ)
    else listOf(SortOrder.NEWEST, SortOrder.AZ, SortOrder.RATING, SortOrder.YEAR)

private fun List<MediaItem>.sortedBy(order: SortOrder): List<MediaItem> = when (order) {
    SortOrder.SERVER -> this
    SortOrder.NEWEST -> sortedByDescending { it.added }
    // Sorteersleutel eenmalig per titel berekenen, niet bij elke vergelijking.
    SortOrder.AZ -> map { TextMatch.cleanTitle(it.name).lowercase() to it }.sortedBy { it.first }.map { it.second }
    SortOrder.RATING -> sortedByDescending { it.rating }
    SortOrder.YEAR -> sortedWith(compareByDescending<MediaItem> { it.year ?: 0 }.thenByDescending { it.added })
}

/** Laatst getoonde lijst per soort; alleen de laatste, want een hele categorie kan 141.000 titels zijn. */
private class BrowseResult(val category: String, val sort: SortOrder, val items: List<MediaItem>) {
    fun itemsFor(category: String, sort: SortOrder) = items.takeIf { category == this.category && sort == this.sort }
}

private val lastBrowse = java.util.WeakHashMap<Library, HashMap<Kind, BrowseResult>>()

@Composable
fun BrowseTab(vm: AppViewModel, library: Library, kind: Kind, initialCategory: String = ALL) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val restorer = rememberFocusRestorer()
    val gridState = rememberLazyGridState()

    val all = library.items(kind)
    // Tellen, filteren en sorteren van tot 141.000 titels gebeurt op de achtergrond, niet tijdens het tekenen.
    val counts by produceState(emptyMap<String?, Int>(), library, kind) {
        value = withContext(Dispatchers.Default) { all.groupingBy { it.categoryId }.eachCount() }
    }
    val categories = remember(library, kind, counts) { library.categories(kind).filter { (counts[it.id] ?: 0) > 0 } }

    var selected by rememberSaveable { mutableStateOf(initialCategory) }
    var sort by rememberSaveable { mutableStateOf(sortOptions(kind).first()) }

    // Terug van een detailscherm: direct de vorige lijst tonen, zodat scrollpositie en focus terugkomen.
    var items by remember { mutableStateOf(lastBrowse[library]?.get(kind)?.itemsFor(selected, sort) ?: emptyList()) }
    LaunchedEffect(library, selected, sort) {
        lastBrowse[library]?.get(kind)?.itemsFor(selected, sort)?.let { items = it; return@LaunchedEffect }
        delay(120) // snel door categorieën bladeren start niet steeds een nieuwe sortering
        items = withContext(Dispatchers.Default) {
            val base = when {
                selected == ALL -> all
                selected.startsWith(GENRE_PREFIX) -> selected.removePrefix(GENRE_PREFIX).let { g -> all.filter { g in genresOf(it) } }
                else -> all.filter { it.categoryId == selected }
            }
            base.sortedBy(sort)
        }
        lastBrowse.getOrPut(library) { HashMap() }[kind] = BrowseResult(selected, sort, items)
    }

    fun select(category: String) {
        if (category == selected) return
        selected = category
        scope.launch { gridState.scrollToItem(0) }
    }

    val catFocus = rememberListFocusMemory()
    Row(Modifier.fillMaxSize()) {
        // Categorieën: focus = selecteren. enterFocusAt zorgt dat je vanuit het raster
        // terugkomt op de geselecteerde categorie in plaats van op de dichtstbijzijnde.
        LazyColumn(
            Modifier.width(260.dp).fillMaxHeight().enterFocusAt(catFocus),
            contentPadding = PaddingValues(start = 36.dp, end = 12.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = ALL) {
                ChipButton(
                    text = if (kind == Kind.LIVE) S.allChannels else S.all,
                    selected = selected == ALL,
                    onClick = { select(ALL) },
                    trailing = all.size.toString(),
                    fill = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) select(ALL) }
                        .restoreFocus(restorer, "cat:$ALL")
                        .listFocusTarget(catFocus, selected == ALL),
                )
            }
            items(categories, key = { it.id }) { c ->
                ChipButton(
                    text = TextMatch.displayTitle(c.name),
                    selected = selected == c.id,
                    onClick = { select(c.id) },
                    trailing = counts[c.id]?.toString(),
                    fill = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) select(c.id) }
                        .restoreFocus(restorer, "cat:${c.id}")
                        .listFocusTarget(catFocus, selected == c.id),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            // Sorteren werkt net als de categorieën: focus = kiezen. enterFocusAt laat je vanuit het raster
            // op de gekozen sortering uitkomen, anders zou je met omhoog per ongeluk anders sorteren.
            val sortFocus = rememberListFocusMemory()
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 48.dp, top = 8.dp).enterFocusAt(sortFocus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortOptions(kind).forEach { option ->
                    fun choose() {
                        if (sort == option) return
                        sort = option
                        scope.launch { gridState.scrollToItem(0) }
                    }
                    ChipButton(
                        text = option.label,
                        selected = sort == option,
                        onClick = ::choose,
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) choose() }
                            .listFocusTarget(sortFocus, sort == option),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatCount(items.size).let { if (kind == Kind.LIVE) S.channelCount(it) else S.titleCount(it) },
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.textMuted,
                )
            }

            LazyVerticalGrid(
                state = gridState,
                columns = if (kind == Kind.LIVE) GridCells.Adaptive(220.dp) else GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                    val focusMod = Modifier.restoreFocus(restorer, "item:${item.key}")
                    if (kind == Kind.LIVE) {
                        ChannelCard(
                            item,
                            onClick = { vm.playLive(context, items, index) },
                            isFavorite = item.key in favorites,
                            onLongClick = { vm.storage.toggleFavorite(item.key) },
                            modifier = focusMod,
                        )
                    } else {
                        PosterCard(
                            item,
                            onClick = { vm.open(item, context) },
                            width = null,
                            isFavorite = item.key in favorites,
                            isWatched = item.key in watched,
                            onLongClick = { vm.storage.toggleFavorite(item.key) },
                            modifier = focusMod,
                        )
                    }
                }
            }
        }
    }
}
