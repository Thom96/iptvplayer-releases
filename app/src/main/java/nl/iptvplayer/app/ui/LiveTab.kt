package nl.iptvplayer.app.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import nl.iptvplayer.app.i18n.S
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.iptvplayer.app.App
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.TextMatch
import nl.iptvplayer.app.player.PlaybackSession
import nl.iptvplayer.app.player.StreamRecovery
import nl.iptvplayer.app.player.fastStartLoadControl



private const val CAT_FAV = "__fav__"
private const val CAT_RECENT = "__recent__"
private const val CAT_ALL = "__all__"
private const val PREVIEW_DELAY_MS = 500L
private val ARROWS = setOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right)


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
    val recovery = remember { StreamRecovery() }
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
        recovery.reset(ch.key)
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
                if (state == Player.STATE_READY) recovery.onPlaying()
            }

            override fun onPlayerError(error: PlaybackException) {
                val ch = currentPlaying ?: return
                when (val action = recovery.onError(ch.key, vm.liveUrl(ch.id, alternative = true), live = true, error.errorCode)) {
                    // Werkt het gekozen formaat niet, probeer dan het andere (ts <-> m3u8).
                    is StreamRecovery.Action.UseFallback -> {
                        player.setMediaItem(exoItem(action.url))
                        player.prepare()
                        player.play()
                    }
                    // Netwerkhapering: even wachten en dezelfde zender opnieuw starten.
                    is StreamRecovery.Action.Retry -> {
                        val key = ch.key
                        retryJob = scope.launch {
                            delay(action.delayMs)
                            if (playingKey == key && !playError) {
                                if (action.toLiveEdge) player.seekToDefaultPosition()
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                    StreamRecovery.Action.GiveUp -> playError = true
                }
            }
        }
        player.addListener(listener)
        val session = context.findActivity()?.let { PlaybackSession(it, player) }
        onDispose {
            player.removeListener(listener)
            session?.release()
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
                        .background(Palette.video, RoundedCornerShape(12.dp))
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
                .background(Palette.video)
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
