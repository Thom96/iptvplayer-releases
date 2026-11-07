package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.iptvplayer.app.App
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.EpgEntry
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.TextMatch
import nl.iptvplayer.app.player.fastStartLoadControl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CAT_FAV = "__fav__"
private const val CAT_RECENT = "__recent__"
private const val CAT_ALL = "__all__"
private const val PREVIEW_DELAY_MS = 500L
private val ARROWS = setOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right)

private val clock = SimpleDateFormat("HH:mm", Locale.ROOT)
private fun hhmm(ms: Long) = clock.format(Date(ms))

/**
 * Live TV in drie kolommen: categorieën, zenders (met wat er nu op is) en een voorbeeld met gids.
 * OK = hetzelfde beeld op volledig scherm; daar zap je met omhoog/omlaag en ga je met Terug weer naar de lijst.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveTab(vm: AppViewModel, library: Library) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val client = vm.client ?: return
    val scope = rememberCoroutineScope()
    val favorites by vm.storage.favorites.collectAsState()
    val recentKeys by vm.storage.recentLive.collectAsState()
    val all = vm.library ?: library
    val fullscreen = vm.liveFullscreen

    // ---------- Categorieën en zenders ----------

    val counts = remember(library) { library.live.groupingBy { it.categoryId }.eachCount() }
    val providerCats = remember(library) { library.liveCategories.filter { (counts[it.id] ?: 0) > 0 } }
    val favChannels = remember(favorites, all) { favorites.mapNotNull { all.byKey[it] }.filter { it.kind == Kind.LIVE } }
    val recentChannels = remember(recentKeys, all) { recentKeys.mapNotNull { all.byKey[it] } }

    var selectedCat by rememberSaveable { mutableStateOf(CAT_ALL) }
    val channels = remember(selectedCat, library, favChannels, recentChannels) {
        when (selectedCat) {
            CAT_FAV -> favChannels
            CAT_RECENT -> recentChannels
            CAT_ALL -> library.live
            else -> library.live.filter { it.categoryId == selectedCat }
        }
    }
    val channelListState = rememberLazyListState()

    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var playingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }
    val focused = focusedKey?.let { all.byKey[it] }
    val playing = playingKey?.let { all.byKey[it] }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // ---------- Speler (één speler voor voorbeeld én volledig scherm) ----------

    val player = remember {
        val dataSource = OkHttpDataSource.Factory(app.http).setUserAgent(App.USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(fastStartLoadControl())
            .build()
    }
    var playError by remember { mutableStateOf(false) }
    var usedFallback by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    val currentPlaying by rememberUpdatedState(playing)

    fun exoItem(url: String) = ExoItem.Builder().setUri(url)
        .apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()

    var startedAt by remember { mutableLongStateOf(0L) }

    fun play(ch: MediaItem) {
        if (playingKey == ch.key && player.playbackState != Player.STATE_IDLE && !playError) return
        playingKey = ch.key
        playError = false
        usedFallback = false
        retries = 0
        startedAt = SystemClock.elapsedRealtime()
        player.setMediaItem(exoItem(vm.liveUrl(ch.id)))
        player.prepare()
        player.play()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                Log.i("IPTV", "Eerste beeld na ${SystemClock.elapsedRealtime() - startedAt} ms (${vm.liveFormat})")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) retries = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                val ch = currentPlaying ?: return
                // Werkt het gekozen formaat niet, probeer dan het andere (ts <-> m3u8).
                if (!usedFallback) {
                    usedFallback = true
                    player.setMediaItem(exoItem(vm.liveUrl(ch.id, alternative = true)))
                    player.prepare()
                    player.play()
                } else if (retries < 3) {
                    // Netwerkhapering: even wachten en dezelfde zender opnieuw starten.
                    retries++
                    val key = ch.key
                    retryJob = scope.launch {
                        delay(2_000L * retries)
                        if (playingKey == key && !playError) {
                            player.prepare()
                            player.play()
                        }
                    }
                } else {
                    playError = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            vm.liveFullscreen = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Weg via de Home-knop tijdens volledig scherm: de gezapte zender toch als recent bewaren.
        if (vm.liveFullscreen) playingKey?.let(vm.storage::addRecentLive)
        retryJob?.cancel() // anders start een geplande herstart de zender op de achtergrond
        player.stop()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    // Voorbeeld pas starten als je even op een zender blijft staan.
    LaunchedEffect(focusedKey) {
        val ch = focused ?: return@LaunchedEffect
        if (vm.liveFullscreen) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        play(ch)
    }

    // ---------- Volledig scherm ----------

    var infoTick by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(infoTick) {
        if (infoTick == 0) return@LaunchedEffect
        infoVisible = true
        delay(5_000)
        infoVisible = false
    }

    fun enterFullscreen(ch: MediaItem) {
        play(ch)
        vm.storage.addRecentLive(ch.key)
        vm.liveFullscreen = true
        infoTick++
    }

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.key == playingKey }.coerceAtLeast(0)
        val next = channels[(idx + delta).mod(channels.size)]
        focusedKey = next.key
        play(next)
        infoTick++
    }

    BackHandler(enabled = fullscreen) {
        // Pas bij het verlaten bijwerken: anders verschuift "Recent bekeken" tijdens het zappen.
        playingKey?.let(vm.storage::addRecentLive)
        vm.liveFullscreen = false
        pendingFocusKey = playingKey
        val idx = channels.indexOfFirst { it.key == playingKey }
        if (idx >= 0) scope.launch { channelListState.scrollToItem(idx) }
    }

    // ---------- Opbouw ----------

    val catFocus = rememberListFocusMemory()
    val channelFocus = rememberListFocusMemory()
    // Komt "Favorieten" of "Recent bekeken" er bovenaan bij, dan die in beeld schuiven in plaats van erboven te verstoppen.
    val catListState = rememberLazyListState()
    LaunchedEffect(favChannels.isEmpty(), recentChannels.isEmpty()) {
        if (catListState.firstVisibleItemIndex <= 2) catListState.scrollToItem(0)
    }

    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var previewRect by remember { mutableStateOf<Rect?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoords = it }
            .onPreviewKeyEvent { e ->
                if (!vm.liveFullscreen || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.DirectionLeft, Key.DirectionRight -> { infoTick++; true }
                    else -> false
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                // Met de pijltjes mag de focus de lijsten niet uit naar de navigatiebalk (dan wissel je per ongeluk
                // van tabblad); dat gaat met Terug.
                .focusProperties { exit = { if (it in ARROWS) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup()
                .alpha(if (fullscreen) 0f else 1f)
                .padding(start = 40.dp, end = 40.dp, top = 8.dp),
        ) {
            // Kolom 1: categorieën
            LazyColumn(
                Modifier.width(220.dp).fillMaxHeight().enterFocusAt(catFocus),
                state = catListState,
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val entries = buildList {
                    if (favChannels.isNotEmpty()) add(Triple(CAT_FAV, S.tabFavorites, favChannels.size))
                    if (recentChannels.isNotEmpty()) add(Triple(CAT_RECENT, S.recentlyWatched, recentChannels.size))
                    add(Triple(CAT_ALL, S.allChannels, library.live.size))
                    providerCats.forEach { add(Triple(it.id, TextMatch.displayTitle(it.name), counts[it.id] ?: 0)) }
                }
                items(entries, key = { it.first }) { (id, name, count) ->
                    fun select() {
                        if (selectedCat == id) return
                        selectedCat = id
                        scope.launch { channelListState.scrollToItem(0) }
                    }
                    ChipButton(
                        text = name,
                        selected = selectedCat == id,
                        onClick = ::select,
                        trailing = formatCount(count),
                        fill = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) select() }
                            .listFocusTarget(catFocus, selectedCat == id),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Kolom 2: zenders
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight().enterFocusAt(channelFocus),
                state = channelListState,
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.key }) { index, ch ->
                    ChannelRow(
                        channel = ch,
                        number = if (ch.num > 0) ch.num else index + 1,
                        nowShowing = vm.epg[ch.id]?.firstOrNull { it.isNow(now) },
                        now = now,
                        isPlaying = ch.key == playingKey,
                        isFavorite = ch.key in favorites,
                        requestFocus = pendingFocusKey == ch.key,
                        onFocusRequested = { pendingFocusKey = null },
                        onFocused = { focusedKey = ch.key },
                        onClick = { enterFullscreen(ch) },
                        onLongClick = { vm.storage.toggleFavorite(ch.key) },
                        modifier = Modifier.listFocusTarget(channelFocus, ch.key == (focusedKey ?: playingKey)),
                    )
                    LaunchedEffect(ch.id) {
                        delay(250)
                        vm.loadEpg(ch.id)
                    }
                }
            }

            Spacer(Modifier.width(20.dp))

            // Kolom 3: voorbeeld + gids
            Column(Modifier.width(330.dp).fillMaxHeight()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .onGloballyPositioned { c -> rootCoords?.let { previewRect = it.localBoundingBoxOf(c) } },
                )
                val info = focused ?: playing
                if (info != null) EpgPanel(vm, info, now)
            }
        }

        // Spelerlaag: ligt over het voorbeeldvak, of over het hele scherm. Zelfde view = geen herladen.
        val rect = previewRect
        val density = LocalDensity.current
        val layer = when {
            fullscreen -> Modifier.fillMaxSize()
            rect != null -> with(density) {
                Modifier
                    .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                    .size(rect.width.toDp(), rect.height.toDp())
            }
            else -> Modifier.size(0.dp)
        }
        Box(
            layer
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (vm.liveFullscreen) infoTick++ else currentPlaying?.let(::enterFullscreen)
                    })
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            when {
                playError -> CenterNote(S.channelUnavailable)
                playingKey == null && !fullscreen -> CenterNote(S.previewHint)
            }
            if (fullscreen && infoVisible && playing != null) {
                FullscreenInfo(playing, vm.epg[playing.id].orEmpty(), now, channels.indexOfFirst { it.key == playing.key })
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: MediaItem,
    number: Int,
    nowShowing: EpgEntry?,
    now: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            requester.requestSafely()
            onFocusRequested()
        }
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tapToClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPlaying) Palette.surface else Color.Transparent,
            contentColor = Palette.text,
            focusedContainerColor = Palette.surfaceFocused,
            focusedContentColor = Palette.text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(12.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(40.dp).alpha(0.6f),
            )
            Box(Modifier.size(width = 56.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = channel.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nowShowing != null) {
                    Text(
                        nowShowing.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(0.7f),
                    )
                    Spacer(Modifier.height(4.dp))
                    ThinProgress(nowShowing.fraction(now))
                }
            }
            if (isFavorite) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Favorite, null, Modifier.size(14.dp), tint = Palette.favorite)
            }
            if (isPlaying) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(8.dp).background(Palette.live, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
private fun ThinProgress(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Palette.accent, RoundedCornerShape(2.dp)))
    }
}

/** Gids onder het voorbeeld: nu en straks. */
@Composable
private fun EpgPanel(vm: AppViewModel, channel: MediaItem, now: Long) {
    LaunchedEffect(channel.id) { vm.loadEpg(channel.id) }
    val entries = vm.epg[channel.id]
    Column(Modifier.padding(top = 16.dp)) {
        Text(channel.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        val upcoming = entries?.filter { it.end > now }?.take(3)
        when {
            entries == null -> Text(S.loadingGuide, color = Palette.textMuted, style = MaterialTheme.typography.bodyMedium)
            upcoming.isNullOrEmpty() -> Text(S.noGuide, color = Palette.textMuted, style = MaterialTheme.typography.bodyMedium)
            else -> upcoming.forEach { e ->
                val current = e.isNow(now)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(if (current) Palette.surface else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current) {
                            Text(
                                S.now,
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.background,
                                modifier = Modifier.background(Palette.accent, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("${hhmm(e.start)} – ${hhmm(e.end)}", style = MaterialTheme.typography.labelMedium, color = Palette.textMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current) {
                        e.description?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                        ThinProgress(e.fraction(now))
                    }
                }
            }
        }
    }
}

/** Informatiebalk onderin bij volledig scherm (na zappen of OK). */
@Composable
private fun FullscreenInfo(channel: MediaItem, epg: List<EpgEntry>, now: Long, index: Int) {
    val current = epg.firstOrNull { it.isNow(now) }
    val next = epg.firstOrNull { it.start >= (current?.end ?: now) }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (channel.num > 0) channel.num else index + 1).toString(),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (current != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${current.title}  ·  ${hhmm(current.start)} – ${hhmm(current.end)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(420.dp)) { ThinProgress(current.fraction(now)) }
                }
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(S.laterAt(hhmm(next.start), next.title), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
            }
            Text(S.zapHint, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, textAlign = TextAlign.Center)
    }
}
