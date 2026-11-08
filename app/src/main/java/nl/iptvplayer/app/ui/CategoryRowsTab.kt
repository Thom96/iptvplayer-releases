package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
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

/** One row in the overview: a category with the newest titles. */
private class CategoryRow(val id: String, val title: String, val total: Int, val items: List<MediaItem>)

/**
 * Movies or series like on Netflix: a horizontal row per category, with "View all"
 * at the end for the full grid with sorting.
 */
@Composable
fun CategoryRowsTab(vm: AppViewModel, library: Library, kind: Kind) {
    val context = LocalContext.current
    val restorer = rememberFocusRestorer()
    val progress by vm.storage.progress.collectAsState()
    var removal by remember { mutableStateOf<Removal?>(null) }
    // Continue watching: for Series the started series, for Movies the started movies.
    val continueItems = progress.filter { (it.seriesId != null) == (kind == Kind.SERIES) }

    // Group and sort all titles in the background, and remember them per library so
    // switching tabs is instant.
    val language = I18n.language // row titles are translated: rebuild after a language change
    val rows by produceState(rowCache[library]?.get(kind to language), library, kind, language) {
        val cached = rowCache[library]?.get(kind to language)
        value = cached ?: withContext(Dispatchers.Default) { buildRows(library, kind) }
            .also { rowCache.getOrPut(library) { HashMap() }[kind to language] = it }
    }
    val shown = rows ?: return

    if (shown.size <= 1 && shown.firstOrNull()?.items.isNullOrEmpty()) {
        CenteredMessage(S.nothingFound, S.nothingFoundHint)
        return
    }
    CategoryRowsList(vm, kind, shown, continueItems, restorer, context) { removal = it }

    removal?.let { r ->
        ConfirmRemoval(r.title, onConfirm = { r.remove(); removal = null }, onDismiss = { removal = null })
    }
}

/** The computed rows per library (language filter); held weakly so old libraries get cleaned up. */
private val rowCache = java.util.WeakHashMap<Library, HashMap<Pair<Kind, AppLanguage>, List<CategoryRow>>>()

private fun buildRows(library: Library, kind: Kind): List<CategoryRow> {
        val all = library.items(kind)
        val byCategory = all.groupBy { it.categoryId }
        return buildList {
            add(CategoryRow(ALL_CATEGORIES, S.recentlyAdded, all.size, all.sortedByDescending { it.added }.take(ROW_SIZE)))
            // Genre rows: only series already have the genre in the provider's list.
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
@OptIn(ExperimentalFoundationApi::class)
private fun CategoryRowsList(
    vm: AppViewModel,
    kind: Kind,
    rows: List<CategoryRow>,
    continueItems: List<Progress>,
    restorer: FocusRestorer,
    context: Context,
    onRemove: (Removal) -> Unit,
) {
    val listState = rememberLazyListState()
    val scroller = rememberSpringScroller(listState)
    val scope = rememberCoroutineScope()
    val rowBasePx = with(LocalDensity.current) { EXPANDING_ROW_BASE_HEIGHT.toPx() }
    // Rows for Continue watching: there the row scrolls itself the default way (no expanding cards).
    val platformSpec = LocalBringIntoViewSpec.current

    /**
     * Put the focused row at the top (the title above it stays visible), at the same place for every row.
     * Info panels above it that are still collapsing already push everything up; the distance takes that into account,
     * and they use the same spring as the scrolling.
     */
    // Keys in list order (same as the items below).
    val keys = remember(rows, continueItems.isEmpty()) {
        buildList {
            if (continueItems.isNotEmpty()) { add("title:continue"); add("row:continue") }
            rows.forEach { add("title:${it.id}"); add("$EXPANDING_ROW_PREFIX${it.id}") }
        }
    }
    fun isExpandingRow(key: Any) = key is String && key.startsWith(EXPANDING_ROW_PREFIX) && key != "row:continue"
    fun keyAt(i: Int): Any? = keys.getOrNull(i)

    /** Measured heights (collapsed) per list item, to place items above the screen. */
    val heights = remember { HashMap<Any, Int>() }

    /** Distance from the top of item [index] to the top of the list, even when it is above the screen. */
    fun topOf(index: Int, visible: List<LazyListItemInfo>): Float? {
        visible.firstOrNull { it.index == index }?.let { return it.offset.toFloat() }
        val first = visible.firstOrNull() ?: return null
        if (index > first.index) return null
        // Above the screen: compute back from the first visible item. Everything in between is collapsed.
        var top = first.offset.toFloat()
        for (i in first.index - 1 downTo index) {
            val key = keyAt(i) ?: return null
            // Row titles all have the same height: a title not measured before gets the height of another.
            val h = when {
                isExpandingRow(key) -> rowBasePx
                else -> (heights[key] ?: heights.entries.firstOrNull { (k, _) -> k != "title:continue" && k.toString().startsWith("title:") }?.value)
                    ?.toFloat() ?: return null
            }
            top -= h
        }
        return top
    }

    fun scrollToRow(titleIndex: Int) {
        val start = listState.layoutInfo.visibleItemsInfo
        start.forEach { if (!isExpandingRow(it.key)) heights[it.key] = it.size }
        if (topOf(titleIndex, start) == null) {
            scope.launch { listState.scrollToItem(titleIndex) }
            return
        }
        scroller.follow {
            val visible = listState.layoutInfo.visibleItemsInfo
            visible.forEach { if (!isExpandingRow(it.key)) heights[it.key] = it.size }
            val top = topOf(titleIndex, visible) ?: return@follow null
            // Info panels above it that are still collapsing already push everything up.
            val stillCollapsing = visible
                .filter { it.index < titleIndex && isExpandingRow(it.key) }
                .sumOf { (it.size - rowBasePx).coerceAtLeast(0f).toDouble() }
            top - stillCollapsing.toFloat()
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
        section(
            S.continueWatching, continueItems, { it.key }, hint = REMOVE_HINT, id = "continue",
            rowWrapper = { row -> CompositionLocalProvider(LocalBringIntoViewSpec provides platformSpec) { row() } },
            onRowFocused = { scrollToRow(0) },
        ) { _, p ->
            ProgressCard(
                p,
                onClick = { vm.resume(context, p) },
                onLongClick = { onRemove(Removal(p.title) { vm.storage.removeProgress(p.key) }) },
                modifier = Modifier.restoreFocus(restorer, "cw:${p.key}"),
            )
        }
        val firstRowIndex = if (continueItems.isEmpty()) 0 else 2
        rows.forEachIndexed { i, row ->
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
            item(key = "$EXPANDING_ROW_PREFIX${row.id}") {
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
                    onFocused = { scrollToRow(firstRowIndex + 2 * i) },
                )
            }
        }
    }
    }
}

private const val EXPANDING_ROW_PREFIX = "row:"

/** The provider's genre names (English, from TMDB), shown as rows in this order; the row title is in `Strings.genres`. */
private val GENRE_KEYS = listOf(
    "comedy", "drama", "crime", "action & adventure", "sci-fi & fantasy", "mystery", "documentary", "reality",
    "animation", "kids", "family", "war & politics", "romance", "thriller", "western", "history", "talk",
)

/** Prefix for genre "categories" in the grid (see BrowseTab). */
const val GENRE_PREFIX = "genre:"

fun genresOf(item: MediaItem): List<String> =
    item.genre?.split(',', '/')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

private fun genreRows(series: List<MediaItem>): List<CategoryRow> {
    val byGenre = HashMap<String, MutableList<MediaItem>>()
    series.forEach { s -> genresOf(s).forEach { g -> byGenre.getOrPut(g) { ArrayList() } += s } }
    return GENRE_KEYS.mapNotNull { genre ->
        val title = S.genres[genre] ?: genre.replaceFirstChar { it.uppercase() }
        val items = byGenre[genre] ?: return@mapNotNull null
        // Skip small genres; only show titles with a poster.
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
                Text(S.seeAll, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                Text(formatCount(total), style = MaterialTheme.typography.bodySmall, color = Color.Unspecified)
            }
        }
    }
}

/** "┃NL┃ AMAZON PRIME VIDEO" -> "Amazon Prime Video"; short abbreviations (HBO, NPO, 4K) stay upper case. */
private fun prettyCategory(name: String): String =
    TextMatch.displayTitle(name).split(' ').joinToString(" ") { w ->
        if (w.length <= 3 || w.any { it.isDigit() }) w else w.lowercase().replaceFirstChar { it.uppercase() }
    }
