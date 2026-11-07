package nl.iptvplayer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.ItemDetails
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.MediaItem

private val CARD_HEIGHT = 192.dp
private val ROW_PADDING = 8.dp
/** Hoogte van een rij zonder infopaneel; de rest is het (uit- of inklappende) paneel. */
val EXPANDING_ROW_BASE_HEIGHT = CARD_HEIGHT + ROW_PADDING * 2
private val POSTER_WIDTH = 128.dp
private val EXPANDED_WIDTH = 341.dp // 16:9 bij dezelfde hoogte
private val cardShape = RoundedCornerShape(10.dp)

/**
 * Rij zoals bij Netflix: de titel waar je op staat klapt uit tot een brede kaart, schuift naar links,
 * en onder de rij verschijnen genre, jaar, duur en beschrijving.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ExpandingPosterRow(
    vm: AppViewModel,
    items: List<MediaItem>,
    restorer: FocusRestorer,
    rowId: String,
    trailing: (@Composable () -> Unit)? = null,
    /** De rij krijgt focus (van boven, onder of de zijbalk): de lijst eromheen kan hem in beeld zetten. */
    onFocused: () -> Unit = {},
) {
    val context = LocalContext.current
    val favorites by vm.storage.favorites.collectAsState()
    val watched by vm.storage.watched.collectAsState()
    val listState = rememberLazyListState()
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var rowFocused by remember { mutableStateOf(false) }
    val focused = items.firstOrNull { it.key == focusedKey }

    // Filmdetails pas ophalen als je even op een titel blijft staan.
    LaunchedEffect(focusedKey) {
        val item = focused ?: return@LaunchedEffect
        delay(300)
        vm.loadDetails(item)
    }

    // Binnenkomen van boven of onder: op de titel waar je in deze rij was (staat links), anders de eerste.
    // Zonder dit kiest Compose de dichtstbijzijnde kaart onder de brede kaart, vaak de tweede.
    val entry = rememberListFocusMemory()
    val entryKey = focusedKey ?: items.firstOrNull()?.key

    val posterPx = with(LocalDensity.current) { POSTER_WIDTH.toPx() }
    val scroller = rememberSpringScroller(listState)
    val scope = rememberCoroutineScope()

    /**
     * Gekozen titel links zetten. De afstand houdt rekening met kaarten links ervan die nog aan het
     * inklappen zijn (bij snel drukken vaak nog niet helemaal uitgeklapt): die schuiven alles al mee.
     * Inklappen en scrollen gebruiken dezelfde veer, zodat het samen één beweging is.
     */
    fun scrollToPivot(index: Int) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            scope.launch { listState.scrollToItem(index) }
            return
        }
        scroller.follow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val info = visible.firstOrNull { it.index == index } ?: return@follow null
            val stillCollapsing = visible.filter { it.index < index }.sumOf { (it.size - posterPx).coerceAtLeast(0f).toDouble() }
            info.offset - stillCollapsing.toFloat()
        }
    }

    Column(
        Modifier.onFocusChanged {
            if (it.hasFocus && !rowFocused) onFocused()
            rowFocused = it.hasFocus
        },
    ) {
      CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyRow(
            state = listState,
            modifier = Modifier.enterFocusAt(entry),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                ExpandingCard(
                    item = item,
                    details = vm.detailsFor(item),
                    expanded = rowFocused && item.key == focusedKey,
                    isFavorite = item.key in favorites,
                    isWatched = item.key in watched,
                    onClick = { vm.open(item, context) },
                    onLongClick = { vm.storage.toggleFavorite(item.key) },
                    modifier = Modifier
                        .restoreFocus(restorer, "$rowId:${item.key}")
                        .listFocusTarget(entry, item.key == entryKey)
                        .onFocusChanged {
                            if (it.isFocused && focusedKey != item.key || it.isFocused && !rowFocused) {
                                scrollToPivot(index)
                                focusedKey = item.key
                            }
                        },
                )
            }
            if (trailing != null) item(key = "trailing") {
                Box(Modifier.onFocusChanged { if (it.hasFocus) scrollToPivot(items.size) }) { trailing() }
            }
        }
      }
        AnimatedVisibility(
            visible = rowFocused && focused != null,
            // Zelfde veer als het scrollen van de lijst, zodat inklappen en verschuiven samen één beweging zijn.
            enter = fadeIn() + expandVertically(moveSpring(IntSize.VisibilityThreshold)),
            exit = fadeOut() + shrinkVertically(moveSpring(IntSize.VisibilityThreshold)),
        ) {
            focused?.let { InfoPanel(it, vm.detailsFor(it)) }
        }
    }
}

@Composable
private fun ExpandingCard(
    item: MediaItem,
    details: ItemDetails?,
    expanded: Boolean,
    isFavorite: Boolean,
    isWatched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        if (expanded) EXPANDED_WIDTH else POSTER_WIDTH,
        animationSpec = moveSpring(Dp.VisibilityThreshold),
        label = "kaartbreedte",
    )
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.tapToClick(onClick, onLongClick).width(width).height(CARD_HEIGHT),
        shape = CardDefaults.shape(cardShape),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = cardShape)),
        colors = CardDefaults.colors(containerColor = Palette.surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            val backdrop = details?.backdrop
            when {
                expanded -> {
                    // Eerst de poster (gedimd als achtergrond, scherp links); de brede afbeelding komt
                    // eroverheen zodra die geladen is, zodat de kaart nooit leeg is.
                    AsyncImage(item.image, null, contentScale = ContentScale.Crop, alpha = 0.3f, modifier = Modifier.fillMaxSize())
                    AsyncImage(
                        item.image, item.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f),
                    )
                    if (backdrop != null) {
                        AsyncImage(backdrop, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 4)
                    }
                    AsyncImage(item.image, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))),
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = if (backdrop == null) 140.dp else 14.dp, end = 14.dp, bottom = 12.dp),
                )
            }
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isWatched) Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = Palette.accent)
                if (isFavorite) Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = Palette.favorite)
            }
        }
    }
}

/** Informatie onder de rij: soort • genre • jaar • duur • beoordeling, en de beschrijving. */
@Composable
private fun InfoPanel(item: MediaItem, details: ItemDetails?) {
    val meta = listOfNotNull(
        if (item.kind == Kind.SERIES) "Serie" else "Film",
        details?.genre?.split(',', '/')?.take(2)?.joinToString(", ") { it.trim() }?.takeIf { it.isNotBlank() },
        (details?.year ?: item.year)?.toString(),
        details?.durationSecs?.let(::formatDuration),
        (details?.rating ?: item.rating.takeIf { it > 0 })?.let { "★ %.1f".format(it) },
    )
    Column(Modifier.padding(start = 56.dp, end = 56.dp, top = 6.dp, bottom = 4.dp).fillMaxWidth(0.62f)) {
        Text(meta.joinToString("  •  "), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                details?.plot != null -> details.plot
                item.kind == Kind.MOVIE && details == null -> "Informatie laden…"
                else -> "Geen beschrijving beschikbaar."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.textMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Kleine rijtitel + uitklappende rij, voor gebruik in een LazyColumn (bijv. Home). */
fun androidx.compose.foundation.lazy.LazyListScope.expandingSection(
    vm: AppViewModel,
    title: String,
    id: String,
    items: List<MediaItem>,
    restorer: FocusRestorer,
) {
    if (items.isEmpty()) return
    item(key = "title:$id") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.textMuted,
            modifier = Modifier.padding(start = 56.dp, top = 12.dp, bottom = 6.dp),
        )
    }
    item(key = "row:$id") { ExpandingPosterRow(vm, items, restorer, id) }
}
