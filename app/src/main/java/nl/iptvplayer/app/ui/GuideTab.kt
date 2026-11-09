package nl.iptvplayer.app.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.iptvplayer.app.App
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.CatchupEntry
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Library
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.TextMatch
import nl.iptvplayer.app.data.XtreamClient
import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import nl.iptvplayer.app.player.fastStartLoadControl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

private const val MINUTE = 60_000L
private const val HALF_HOUR = 30 * MINUTE
private const val DAY = 24 * 60 * MINUTE
/** Visible part of the timeline. */
private const val WINDOW_MS = 2 * 60 * MINUTE
private const val PREVIEW_DELAY_MS = 700L
private const val PAGE_ROWS = 5
private const val GUIDE_ALL = "__all__"
private const val GUIDE_FAV = "__fav__"

private val ChannelColumn = 168.dp
private val RowHeight = 46.dp
private val RowGap = 4.dp
private val Shape = RoundedCornerShape(8.dp)

private fun floorHalfHour(ms: Long) = ms / HALF_HOUR * HALF_HOUR
private fun defaultWindowStart(now: Long) = floorHalfHour(now) - HALF_HOUR

private fun dayKey(ms: Long): Int = Calendar.getInstance().run {
    timeInMillis = ms
    get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
}

private fun guideDayLabel(ms: Long, now: Long): String = when (dayKey(ms)) {
    dayKey(now) -> S.today
    dayKey(now + DAY) -> S.tomorrow
    dayKey(now - DAY) -> S.yesterday
    else -> SimpleDateFormat("EEE d MMM", appLocale).format(Date(ms)).replaceFirstChar { it.uppercase(appLocale) }
}

private fun List<CatchupEntry>.at(time: Long) = firstOrNull { time >= it.start && time < it.end }

/** A finished programme that can be watched again via the provider's archive. */
private fun canReplay(channel: MediaItem, e: CatchupEntry, now: Long) =
    channel.archiveDays > 0 && e.serverStart.isNotEmpty() && e.end <= now && e.start >= now - channel.archiveDays * DAY

/**
 * TV guide: preview and programme details at the top, below that a timeline per channel.
 * The grid is one focus target that moves a selection itself (much faster than a focusable cell per
 * programme with thousands of channels): left/right = previous/next programme, up/down = channel,
 * OK = watch live, or watch a finished programme again if the channel has an archive.
 */
@OptIn(UnstableApi::class)
@Composable
fun GuideTab(vm: AppViewModel, library: Library) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    if (vm.client == null) return
    val scope = rememberCoroutineScope()
    val favorites by vm.storage.favorites.collectAsState()
    val all = vm.library ?: library

    // ---------- Categories and channels ----------

    val counts = remember(library) { library.live.groupingBy { it.categoryId }.eachCount() }
    val favChannels = remember(favorites, all) { favorites.mapNotNull { all.byKey[it] }.filter { it.kind == Kind.LIVE } }
    val categories = remember(library, favChannels, counts) {
        buildList {
            if (favChannels.isNotEmpty()) add(GUIDE_FAV to S.tabFavorites)
            add(GUIDE_ALL to S.allChannels)
            library.liveCategories.filter { (counts[it.id] ?: 0) > 0 }.forEach { add(it.id to TextMatch.displayTitle(it.name)) }
        }
    }
    var selectedCat by rememberSaveable { mutableStateOf(GUIDE_ALL) }
    val channels = remember(selectedCat, library, favChannels) {
        when (selectedCat) {
            GUIDE_FAV -> favChannels
            GUIDE_ALL -> library.live
            else -> library.live.filter { it.categoryId == selectedCat }
        }
    }

    // ---------- Selection ----------

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var selRow by rememberSaveable { mutableStateOf(0) }
    var focusTime by rememberSaveable { mutableLongStateOf(now) }
    var windowStart by rememberSaveable { mutableLongStateOf(defaultWindowStart(now)) }
    var gridFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val row = selRow.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    val channel = channels.getOrNull(row)
    val selected = channel?.let { vm.guide[it.id]?.at(focusTime) }

    val earliest = now - XtreamClient.GUIDE_PAST_MS
    val latest = now + XtreamClient.GUIDE_FUTURE_MS

    fun moveRow(delta: Int) {
        if (channels.isEmpty()) return
        selRow = (row + delta).coerceIn(0, channels.size - 1)
    }

    fun moveRight() {
        val list = channel?.let { vm.guide[it.id] }.orEmpty()
        val after = list.at(focusTime)?.end ?: (focusTime + 1)
        val t = list.firstOrNull { it.start >= after }?.start ?: (floorHalfHour(focusTime) + HALF_HOUR)
        if (t >= latest) return
        focusTime = t
        if (t >= windowStart + WINDOW_MS - HALF_HOUR) windowStart = floorHalfHour(t) - HALF_HOUR
    }

    /** False = already at the start of the guide (focus may then leave the grid). */
    fun moveLeft(): Boolean {
        val list = channel?.let { vm.guide[it.id] }.orEmpty()
        val before = list.at(focusTime)?.start ?: focusTime
        val prev = list.lastOrNull { it.start < before }
        val t = prev?.start ?: (floorHalfHour(before - 1))
        if (before <= earliest) return false
        val target = t.coerceAtLeast(earliest)
        if (target < windowStart) {
            // Show the end of a long programme rather than jumping hours back.
            val end = prev?.end ?: (target + HALF_HOUR)
            windowStart = floorHalfHour(maxOf(target, end - WINDOW_MS / 2)).coerceAtLeast(floorHalfHour(earliest))
            focusTime = maxOf(target, windowStart)
        } else {
            focusTime = target
        }
        return true
    }

    fun backToNow() {
        focusTime = now
        windowStart = defaultWindowStart(now)
    }

    fun activate() {
        val ch = channel ?: return
        val e = selected
        when {
            e != null && canReplay(ch, e, now) -> vm.playCatchup(context, ch, e)
            e == null || (e.start <= now && e.end > now) -> vm.playLive(context, channels, row)
        }
    }

    // Keep the selected row visible.
    LaunchedEffect(row, channels) {
        val info = listState.layoutInfo
        val first = listState.firstVisibleItemIndex
        val fully = info.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset }
            .coerceAtLeast(1)
        when {
            row < first -> listState.scrollToItem(row)
            row >= first + fully -> listState.scrollToItem((row - fully + 1).coerceAtLeast(0))
        }
    }

    BackHandler(enabled = gridFocused && (windowStart != defaultWindowStart(now) || focusTime !in now - HALF_HOUR..now + HALF_HOUR)) {
        backToNow()
    }

    // ---------- Preview player ----------

    val player = remember {
        val dataSource = OkHttpDataSource.Factory(app.http).setUserAgent(App.USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(fastStartLoadControl())
            .build()
    }
    var playingKey by remember { mutableStateOf<String?>(null) }
    var playError by remember { mutableStateOf(false) }
    var triedFallback by remember { mutableStateOf(false) }
    val playingChannel by rememberUpdatedState(playingKey?.let { all.byKey[it] })

    fun exoItem(url: String) = ExoItem.Builder().setUri(url)
        .apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()

    fun preview(ch: MediaItem) {
        if (playingKey == ch.key && !playError) return
        playingKey = ch.key
        playError = false
        triedFallback = false
        player.setMediaItem(exoItem(vm.liveUrl(ch.id)))
        player.prepare()
        player.play()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val ch = playingChannel ?: return
                if (!triedFallback) {
                    // The chosen format doesn't work: try the other one (ts <-> m3u8) once.
                    triedFallback = true
                    player.setMediaItem(exoItem(vm.liveUrl(ch.id, alternative = true)))
                    player.prepare()
                    player.play()
                } else {
                    playError = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (player.mediaItemCount > 0) {
            playError = false
            player.prepare()
            player.play()
        }
    }
    // Only start the preview once you are in the guide and stay on a channel for a moment.
    LaunchedEffect(channel?.key, gridFocused) {
        val ch = channel ?: return@LaunchedEffect
        if (!gridFocused) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        preview(ch)
    }

    // ---------- Layout ----------

    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(144.dp)) {
            Box(
                Modifier
                    .width(256.dp)
                    .fillMaxHeight()
                    .background(Palette.video, RoundedCornerShape(12.dp))
                    .clipToBounds(),
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
                    playingKey == null -> CenterNote(S.previewHint)
                }
            }
            Spacer(Modifier.width(20.dp))
            if (channel != null) ProgrammeDetails(channel, row, selected, vm.guide.containsKey(channel.id), now)
        }

        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.first }) { (id, name) ->
                fun select() {
                    if (selectedCat == id) return
                    selectedCat = id
                    selRow = 0
                    scope.launch { listState.scrollToItem(0) }
                }
                ChipButton(
                    text = name,
                    selected = selectedCat == id,
                    onClick = ::select,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) select() },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> if (row == 0) false else { moveRow(-1); true }
                        Key.DirectionDown -> { moveRow(1); true }
                        Key.ChannelUp, Key.PageUp -> { moveRow(-PAGE_ROWS); true }
                        Key.ChannelDown, Key.PageDown -> { moveRow(PAGE_ROWS); true }
                        Key.DirectionRight -> { moveRight(); true }
                        Key.DirectionLeft -> moveLeft()
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { activate(); true }
                        else -> false
                    }
                }
                .onFocusChanged { gridFocused = it.isFocused }
                .focusable(),
        ) {
            val timelineWidth = maxWidth - ChannelColumn - 8.dp
            val dpPerMs = timelineWidth.value / WINDOW_MS
            val windowEnd = windowStart + WINDOW_MS

            Column(Modifier.fillMaxSize()) {
                // Header: day and times
                Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        guideDayLabel(focusTime, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.accent,
                        modifier = Modifier.width(ChannelColumn + 8.dp),
                    )
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                        for (i in 0 until (WINDOW_MS / HALF_HOUR).toInt()) {
                            val t = windowStart + i * HALF_HOUR
                            Text(
                                hhmm(t),
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.textMuted,
                                modifier = Modifier.offset(x = Dp((t - windowStart) * dpPerMs) + 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    state = listState,
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    itemsIndexed(channels, key = { _, ch -> ch.key }) { index, ch ->
                        GuideRow(
                            channel = ch,
                            number = if (ch.num > 0) ch.num else index + 1,
                            entries = vm.guide[ch.id],
                            isSelectedRow = index == row,
                            gridFocused = gridFocused,
                            focusTime = focusTime,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            dpPerMs = dpPerMs,
                            timelineWidth = timelineWidth,
                            now = now,
                        )
                        LaunchedEffect(ch.id) {
                            delay(200)
                            vm.loadGuide(ch.id)
                        }
                    }
                }
            }
            // "Now" line over the timeline
            if (now in windowStart until windowEnd) {
                Box(
                    Modifier
                        .offset(x = ChannelColumn + 8.dp + Dp((now - windowStart) * dpPerMs))
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Palette.live),
                )
            }
        }
    }
}

@Composable
private fun ProgrammeDetails(channel: MediaItem, index: Int, entry: CatchupEntry?, loaded: Boolean, now: Long) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val number = if (channel.num > 0) channel.num else index + 1
        Text(
            "$number  ${channel.title}",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry == null) {
            Text(
                if (loaded) S.noGuide else S.loadingGuide,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.textSoft,
            )
            Spacer(Modifier.weight(1f))
            Text(S.guideWatchLive, style = MaterialTheme.typography.labelMedium, color = Palette.textMuted)
            return
        }
        val isNow = now >= entry.start && now < entry.end
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isNow) {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.background(Palette.live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            val day = if (dayKey(entry.start) == dayKey(now)) "" else guideDayLabel(entry.start, now) + " · "
            Text(
                "$day${hhmm(entry.start)} – ${hhmm(entry.end)}",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.textSoft,
            )
        }
        Text(
            entry.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        val hint = when {
            isNow -> S.guideWatchLive
            canReplay(channel, entry, now) -> S.guideWatchArchive
            entry.start > now -> S.guideStartsAt(hhmm(entry.start))
            else -> null
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = Palette.accent)
    }
}

@Composable
private fun GuideRow(
    channel: MediaItem,
    number: Int,
    entries: List<CatchupEntry>?,
    isSelectedRow: Boolean,
    gridFocused: Boolean,
    focusTime: Long,
    windowStart: Long,
    windowEnd: Long,
    dpPerMs: Float,
    timelineWidth: Dp,
    now: Long,
) {
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        // Channel
        Row(
            Modifier
                .width(ChannelColumn)
                .fillMaxHeight()
                .background(if (isSelectedRow) Palette.surfaceFocused else Palette.surface, Shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.textMuted,
                modifier = Modifier.width(30.dp),
            )
            Box(Modifier.size(width = 40.dp, height = 26.dp), contentAlignment = Alignment.Center) {
                AsyncImage(channel.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(8.dp))
            Text(channel.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))

        // Timeline
        Box(Modifier.width(timelineWidth).fillMaxHeight().clipToBounds()) {
            val visible = entries?.filter { it.end > windowStart && it.start < windowEnd }.orEmpty()
            if (visible.isEmpty()) {
                ProgrammeBlock(
                    title = if (entries == null) S.loadingGuide else S.noGuide,
                    time = null,
                    x = 0.dp,
                    width = timelineWidth,
                    selected = isSelectedRow,
                    focused = gridFocused,
                    past = false,
                )
            } else {
                visible.forEach { e ->
                    val from = maxOf(e.start, windowStart)
                    val to = minOf(e.end, windowEnd)
                    ProgrammeBlock(
                        title = e.title,
                        time = hhmm(e.start),
                        x = Dp((from - windowStart) * dpPerMs),
                        width = Dp((to - from) * dpPerMs) - 3.dp,
                        selected = isSelectedRow && focusTime >= e.start && focusTime < e.end,
                        focused = gridFocused,
                        past = e.end <= now,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(title: String, time: String?, x: Dp, width: Dp, selected: Boolean, focused: Boolean, past: Boolean) {
    if (width < 2.dp) return
    val showBorder = selected && focused
    Box(
        Modifier
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .background(if (selected) Palette.surfaceFocused else Palette.surface, Shape)
            .then(if (showBorder) Modifier.border(BorderStroke(3.dp, Palette.accent), Shape) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (width < 28.dp) return@Box
        Column(Modifier.alpha(if (past && !selected) 0.55f else 1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (time != null && width >= 72.dp) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = Palette.textMuted, maxLines = 1)
            }
        }
    }
}
