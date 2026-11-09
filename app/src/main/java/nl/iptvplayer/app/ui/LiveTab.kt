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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
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
import nl.iptvplayer.app.player.timeshift.LiveTimeshift
import nl.iptvplayer.app.player.timeshift.TimeshiftBar
import nl.iptvplayer.app.player.timeshift.liveMediaItem
import nl.iptvplayer.app.player.timeshift.rememberTimeshiftState



private const val CAT_FAV = "__fav__"
private const val CAT_RECENT = "__recent__"
private const val CAT_ALL = "__all__"
private const val PREVIEW_DELAY_MS = 500L
private val CAT_EXPANDED = 220.dp
private val CAT_COLLAPSED = 52.dp
private val ARROWS = setOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right)


/**
 * Live TV in three columns: categories, channels (with what's on now) and a preview with guide.
 * OK = the same picture full screen; there you zap with up/down and Back takes you to the list again.
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

    // ---------- Categories and channels ----------

    val counts = remember(library) { library.live.groupingBy { it.categoryId }.eachCount() }
    val providerCats = remember(library) { library.liveCategories.filter { (counts[it.id] ?: 0) > 0 } }
    val favChannels = remember(favorites, all) { favorites.mapNotNull { all.byKey[it] }.filter { it.kind == Kind.LIVE } }
    val recentChannels = remember(recentKeys, all) { recentKeys.mapNotNull { all.byKey[it] } }

    var selectedCat by rememberSaveable { mutableStateOf(CAT_ALL) }
    var catHasFocus by remember { mutableStateOf(false) }
    val catEntries = remember(favChannels, recentChannels, library, providerCats, counts) {
        buildList {
            if (favChannels.isNotEmpty()) add(Triple(CAT_FAV, S.tabFavorites, favChannels.size))
            if (recentChannels.isNotEmpty()) add(Triple(CAT_RECENT, S.recentlyWatched, recentChannels.size))
            add(Triple(CAT_ALL, S.allChannels, library.live.size))
            providerCats.forEach { add(Triple(it.id, TextMatch.displayTitle(it.name), counts[it.id] ?: 0)) }
        }
    }
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
            // Every 10 s, so the progress bars next to the channels stay current.
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    // ---------- Player (one player for both preview and full screen) ----------

    // Pause and rewind: the channel is recorded on the device while it plays (off in Settings = direct).
    val timeshift = remember {
        val minutes = vm.storage.timeshiftMinutes.value
        if (minutes > 0) LiveTimeshift(context, app.http, App.USER_AGENT, minutes * 60_000L) else null
    }
    val player = remember {
        val dataSource = OkHttpDataSource.Factory(app.http).setUserAgent(App.USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(timeshift?.dataSourceFactory(dataSource) ?: dataSource))
            .setLoadControl(fastStartLoadControl())
            .build()
    }
    var playError by remember { mutableStateOf(false) }
    val recovery = remember { StreamRecovery() }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    val currentPlaying by rememberUpdatedState(playing)

    fun exoItem(url: String) = timeshift.liveMediaItem(ExoItem.Builder(), url)
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
                    // If the chosen format doesn't work, try the other one (ts <-> m3u8).
                    is StreamRecovery.Action.UseFallback -> {
                        player.setMediaItem(exoItem(action.url))
                        player.prepare()
                        player.play()
                    }
                    // Network stall: wait a moment and restart the same channel.
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
            timeshift?.release()
            vm.liveFullscreen = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Left via the Home button during full screen: still save the zapped channel as recent.
        if (vm.liveFullscreen) playingKey?.let(vm.storage::addRecentLive)
        retryJob?.cancel() // otherwise a scheduled restart starts the channel in the background
        player.stop()
        timeshift?.stopRecording() // frees the connection; playing again starts a new recording
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    // Only start the preview when you stay on a channel for a moment.
    LaunchedEffect(focusedKey) {
        val ch = focused ?: return@LaunchedEffect
        if (vm.liveFullscreen) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        play(ch)
    }

    // ---------- Full screen ----------

    var infoTick by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(infoTick) {
        if (infoTick == 0) return@LaunchedEffect
        infoVisible = true
        delay(5_000)
        infoVisible = false
    }

    val shift by rememberTimeshiftState(timeshift, player)
    var paused by remember { mutableStateOf(false) }
    fun togglePause() {
        if (player.isPlaying) player.pause() else player.play()
        paused = !player.playWhenReady
        infoTick++
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
        paused = false
        play(next)
        infoTick++
    }

    BackHandler(enabled = fullscreen) {
        // Only update when leaving: otherwise "Recently watched" shifts while zapping.
        playingKey?.let(vm.storage::addRecentLive)
        vm.liveFullscreen = false
        player.play()
        paused = false
        pendingFocusKey = playingKey
        val idx = channels.indexOfFirst { it.key == playingKey }
        if (idx >= 0) scope.launch { channelListState.scrollToItem(idx) }
    }
    // Rewound: Back first returns to live (registered later, so it goes first).
    BackHandler(enabled = fullscreen && shift?.isLive == false) {
        timeshift?.goLive(player)
        player.play()
        paused = false
        infoTick++
    }

    // ---------- Layout ----------

    val catFocus = rememberListFocusMemory()
    val channelFocus = rememberListFocusMemory()
    // If "Favourites" or "Recently watched" appears at the top, scroll it into view instead of hiding it above.
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
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause ->
                        { if (shift != null) togglePause() else infoTick++; true }
                    Key.DirectionLeft, Key.MediaRewind -> { timeshift?.seekBy(player, -LiveTimeshift.STEP_MS); infoTick++; true }
                    Key.DirectionRight, Key.MediaFastForward -> { timeshift?.seekBy(player, LiveTimeshift.STEP_MS); infoTick++; true }
                    else -> false
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                // With the arrow keys the focus must not leave the lists to the navigation rail (you'd switch
                // tabs by accident); that goes with Back.
                // Exception: left from the categories goes to the side rail (which puts focus on this tab).
                .focusProperties {
                    exit = {
                        if (it in ARROWS && !(it == FocusDirection.Left && catHasFocus)) FocusRequester.Cancel
                        else FocusRequester.Default
                    }
                }
                .focusGroup()
                .alpha(if (fullscreen) 0f else 1f)
                .padding(start = 40.dp, end = 40.dp, top = 8.dp),
        ) {
            // Column 1: categories. Collapses to a narrow strip with the selected category as soon as focus is
            // in the channel list; left expands it again.
            val catWidth by animateDpAsState(if (catHasFocus) CAT_EXPANDED else CAT_COLLAPSED, label = "catWidth")
            val expandedness = ((catWidth - CAT_COLLAPSED) / (CAT_EXPANDED - CAT_COLLAPSED)).coerceIn(0f, 1f)
            // Always takes the narrow width in the row; expanded, the column lies over the channel list (like the
            // side rail), so the list doesn't get narrower.
            Box(Modifier.width(CAT_COLLAPSED).fillMaxHeight().zIndex(1f)) {
                Box(
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(catWidth)
                        .fillMaxHeight()
                        .background(Palette.background.copy(alpha = expandedness))
                        .onFocusChanged { catHasFocus = it.hasFocus },
                ) {
                    LazyColumn(
                        // Collapsed it is invisible but still there: left from the channels lands here (on the selected
                        // category) and expands the column again.
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .alpha(expandedness)
                            // At the top and bottom of the list, don't jump to a channel under the expanded column.
                            .focusProperties {
                                exit = { if (it == FocusDirection.Up || it == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default }
                            }
                            .enterFocusAt(catFocus),
                        state = catListState,
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(catEntries, key = { it.first }) { (id, name, count) ->
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
                    if (expandedness < 1f) {
                        CollapsedCategory(
                            name = catEntries.firstOrNull { it.first == selectedCat }?.second.orEmpty(),
                            modifier = Modifier.width(CAT_COLLAPSED).fillMaxHeight().padding(bottom = 32.dp).alpha(1f - expandedness),
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // Column 2: channels
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

            // Column 3: preview + guide
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

        // Player layer: lies over the preview box, or over the whole screen. Same view = no reloading.
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
            val liveShift = shift
            // While paused or rewound the bar stays, so you can see how far behind live you are.
            val keepInfo = liveShift != null && (paused || !liveShift.isLive)
            if (fullscreen && (infoVisible || keepInfo) && playing != null) {
                FullscreenInfo(
                    playing, vm.epg[playing.id].orEmpty(), now, channels.indexOfFirst { it.key == playing.key },
                    hint = if (liveShift != null) S.timeshiftKeys else S.zapHint,
                ) {
                    if (liveShift != null) {
                        Spacer(Modifier.height(16.dp))
                        TimeshiftBar(liveShift)
                    }
                }
            }
        }
    }
}
