package nl.iptvplayer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
    // Compute the sort key once per title, not on every comparison.
    SortOrder.AZ -> map { TextMatch.cleanTitle(it.name).lowercase() to it }.sortedBy { it.first }.map { it.second }
    SortOrder.RATING -> sortedByDescending { it.rating }
    SortOrder.YEAR -> sortedWith(compareByDescending<MediaItem> { it.year ?: 0 }.thenByDescending { it.added })
}

/** Last shown list per kind; only the last one, because a whole category can be 141,000 titles. */
/** Height (fraction of the grid) where the top of the focused row stays. */
private const val GRID_PIVOT = 0.2f

private class BrowseResult(val category: String, val sort: SortOrder, val items: List<MediaItem>) {
    fun itemsFor(category: String, sort: SortOrder) = items.takeIf { category == this.category && sort == this.sort }
}

private val lastBrowse = java.util.WeakHashMap<Library, HashMap<Kind, BrowseResult>>()

/** Where you were per category and sort order: scroll position and the focused title. Survives switching tabs. */
private class GridPosition(val index: Int, val offset: Int, val focusedKey: String?)

private val gridPositions = java.util.WeakHashMap<Library, HashMap<String, GridPosition>>()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseTab(vm: AppViewModel, library: Library, kind: Kind, initialCategory: String = ALL) {
    val context = LocalContext.current
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val restorer = rememberFocusRestorer()
    val gridState = rememberLazyGridState()
    val positions = remember(library) { gridPositions.getOrPut(library) { HashMap() } }
    val gridFocus = rememberKeyedFocusMemory()
    /** Category and sort order whose saved position should be applied once its titles are shown. */
    var restorePending by remember { mutableStateOf<String?>(null) }

    val all = library.items(kind)
    // Counting, filtering and sorting up to 141,000 titles happens in the background, not while drawing.
    val counts by produceState(emptyMap<String?, Int>(), library, kind) {
        value = withContext(Dispatchers.Default) { all.groupingBy { it.categoryId }.eachCount() }
    }
    val categories = remember(library, kind, counts) { library.categories(kind).filter { (counts[it.id] ?: 0) > 0 } }

    var selected by rememberSaveable { mutableStateOf(initialCategory) }
    var sort by rememberSaveable { mutableStateOf(sortOptions(kind).first()) }
    fun positionKey() = "$kind|$selected|$sort"
    fun savePosition() {
        // While another category is still loading, the grid still shows the previous one: nothing to save.
        if (restorePending != null) return
        positions[positionKey()] = GridPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, gridFocus.lastKey)
    }
    /** After switching category or sort order: back to where you were in it, or to the top. */
    suspend fun restorePosition() {
        val key = restorePending ?: return
        restorePending = null
        val saved = positions[key]
        gridFocus.lastKey = saved?.focusedKey
        gridState.scrollToItem(saved?.index ?: 0, saved?.offset ?: 0)
    }
    remember(gridFocus) { gridFocus.lastKey = positions[positionKey()]?.focusedKey }
    DisposableEffect(Unit) { onDispose { savePosition() } }

    // Back from a detail screen: show the previous list right away, so scroll position and focus come back.
    var items by remember { mutableStateOf(lastBrowse[library]?.get(kind)?.itemsFor(selected, sort) ?: emptyList()) }
    LaunchedEffect(library, selected, sort) {
        val cached = lastBrowse[library]?.get(kind)?.itemsFor(selected, sort)
        if (cached != null) {
            items = cached
            restorePosition()
            return@LaunchedEffect
        }
        delay(120) // quickly paging through categories doesn't start a new sort every time
        items = withContext(Dispatchers.Default) {
            val base = when {
                selected == ALL -> all
                selected.startsWith(GENRE_PREFIX) -> selected.removePrefix(GENRE_PREFIX).let { g -> all.filter { g in genresOf(it) } }
                else -> all.filter { it.categoryId == selected }
            }
            base.sortedBy(sort)
        }
        lastBrowse.getOrPut(library) { HashMap() }[kind] = BrowseResult(selected, sort, items)
        restorePosition()
    }

    fun select(category: String) {
        if (category == selected) return
        savePosition()
        selected = category
        restorePending = positionKey()
    }

    val catFocus = rememberListFocusMemory()
    Row(Modifier.fillMaxSize()) {
        // Categories: focus = select. enterFocusAt makes sure that coming from the grid you
        // land on the selected category instead of the nearest one.
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
            // Sorting works just like the categories: focus = choose. enterFocusAt makes you land on the chosen
            // sort order coming from the grid, otherwise pressing up would change the sort by accident.
            val sortFocus = rememberListFocusMemory()
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 48.dp, top = 8.dp).enterFocusAt(sortFocus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortOptions(kind).forEach { option ->
                    fun choose() {
                        if (sort == option) return
                        savePosition()
                        sort = option
                        restorePending = positionKey()
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

            // The row with the focus stays at the same height; the grid slides underneath.
            val pivot = remember(gridState) {
                PivotBringIntoView(GRID_PIVOT, { gridState.canScrollBackward }, { gridState.canScrollForward })
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides pivot) {
            LazyVerticalGrid(
                state = gridState,
                columns = if (kind == Kind.LIVE) GridCells.Adaptive(220.dp) else GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().calmHeldKeys().enterFocusAt(gridFocus),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                    val focusMod = Modifier
                        .restoreFocus(restorer, "item:${item.key}")
                        .keyedFocusTarget(gridFocus, item.key)
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
}
