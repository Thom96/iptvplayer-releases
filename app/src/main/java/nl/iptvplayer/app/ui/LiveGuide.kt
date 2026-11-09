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
private const val WINDOW_MS = 150 * MINUTE
private const val PREVIEW_DELAY_MS = 700L
private const val PAGE_ROWS = 5

private val ChannelColumn = 168.dp
private val RowHeight = 46.dp
private val RowGap = 4.dp
private val Shape = RoundedCornerShape(8.dp)

private fun floorHalfHour(ms: Long) = ms / HALF_HOUR * HALF_HOUR
/** An hour of the past is in view, so earlier programmes are easy to find. */
private fun defaultWindowStart(now: Long) = floorHalfHour(now) - 2 * HALF_HOUR

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
 * TV guide inside the Live tab: preview and programme details at the top, below that a timeline per channel.
 * The grid is one focus target that moves a selection itself (much faster than a focusable cell per
 * programme with thousands of channels): left/right = previous/next programme, up/down = channel,
 * OK = watch live, or watch a finished programme again if the channel has an archive.
 *
 * The Live tab owns the player: [previewBox] marks where the preview goes, [onPreview] asks to play a channel
 * there and [onWatchLive] opens it full screen.
 */
@Composable
internal fun LiveGuide(
    vm: AppViewModel,
    channels: List<MediaItem>,
    previewBox: Modifier,
    onPreview: (MediaItem) -> Unit,
    onWatchLive: (MediaItem) -> Unit,
    /** Category chips above the timeline. */
    categories: @Composable () -> Unit,
) {
    val context = LocalContext.current

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
    LaunchedEffect(channels) {
        selRow = 0
        listState.scrollToItem(0)
    }

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
        if (before <= earliest) return false
        val prev = list.lastOrNull { it.start < before }
        val target = (prev?.start ?: floorHalfHour(before - 1)).coerceAtLeast(earliest)
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

    fun activate() {
        val ch = channel ?: return
        val e = selected
        when {
            e != null && canReplay(ch, e, now) -> vm.playCatchup(context, ch, e)
            e == null || (e.start <= now && e.end > now) -> onWatchLive(ch)
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

    // Back first returns the timeline to now.
    BackHandler(
        enabled = gridFocused && !vm.liveFullscreen &&
            (windowStart != defaultWindowStart(now) || focusTime !in now - HALF_HOUR..now + HALF_HOUR),
    ) {
        focusTime = now
        windowStart = defaultWindowStart(now)
    }

    // The selected channel gets its guide first, and plays in the preview after a moment.
    LaunchedEffect(channel?.id) { channel?.let { vm.loadGuide(it.id, priority = true) } }
    LaunchedEffect(channel?.key, gridFocused) {
        val ch = channel ?: return@LaunchedEffect
        if (!gridFocused) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        onPreview(ch)
    }

    // ---------- Layout ----------

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(132.dp)) {
            Box(previewBox.width(235.dp).fillMaxHeight().background(Palette.video, RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(20.dp))
            if (channel != null) ProgrammeDetails(channel, row, selected, vm.guide.containsKey(channel.id), now)
        }

        Spacer(Modifier.height(8.dp))
        categories()
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || vm.liveFullscreen) return@onPreviewKeyEvent false
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
        val hint: String = when {
            isNow -> S.guideWatchLive
            canReplay(channel, entry, now) -> S.guideWatchArchive
            entry.start > now -> S.guideStartsAt(hhmm(entry.start))
            else -> S.guideNoReplay
        }
        Text(hint, style = MaterialTheme.typography.labelMedium, color = if (hint == S.guideNoReplay) Palette.textMuted else Palette.accent)
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
                        past = e.end <= now && !canReplay(channel, e, now),
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
