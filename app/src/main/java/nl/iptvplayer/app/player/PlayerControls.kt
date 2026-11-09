package nl.iptvplayer.app.player

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import nl.iptvplayer.app.i18n.S
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.SegmentType
import nl.iptvplayer.app.data.SkipSegment
import nl.iptvplayer.app.ui.Palette
import nl.iptvplayer.app.ui.formatTime
import nl.iptvplayer.app.ui.requestSafely



private const val HIDE_AFTER_MS = 4_000L

private enum class Panel { NONE, TRACKS, SPEED }


/**
 * Controls during playback, in the style of Netflix with the colours of the logo:
 * top left back / 10 s back / next, top right the title, at the bottom the play button, seek bar
 * and a bar with audio and subtitles.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    player: ExoPlayer,
    queue: PlaybackQueue,
    onExit: () -> Unit,
    onControlsVisible: (Boolean) -> Unit,
    /** To the next episode; the activity first saves the current one as finished. */
    onNextEpisode: () -> Unit,
    /** Series intro: S.episodesAndMore opens the series page. */
    onMoreEpisodes: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    /** Intro, recap and credits times of an episode (from the database); empty if unknown. */
    loadSegments: suspend (PlayItem) -> List<SkipSegment> = { emptyList() },
    /** The intro the app learned for this series from the viewer's seeking. */
    learnedIntro: (PlayItem) -> SkipSegment? = { null },
    /** Countdown before the next episode starts by itself; 0 = never automatically. */
    autoNextMs: Long = 5_000,
) {
    // ---------- Player state ----------
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var index by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
                // Don't wait for the next measurement: with the times of the previous episode the
                // "next episode" button would stay and skip ahead once more.
                position = 0
                duration = 0
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) { speed = p.speed }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
            buffered = player.bufferedPosition.coerceAtLeast(0)
            delay(500)
        }
    }

    val item = queue.items.getOrNull(index)
    val isLive = queue.isLive
    val hasNext = !isLive && index < queue.items.size - 1
    val nextItem = if (hasNext) queue.items[index + 1].takeIf { it.kind == Kind.EPISODE } else null

    // ---------- Skip intro and credits ----------
    var segments by remember { mutableStateOf(emptyList<SkipSegment>()) }
    LaunchedEffect(index) {
        segments = emptyList()
        segments = item?.takeIf { it.kind == Kind.EPISODE }?.let { loadSegments(it) }.orEmpty()
    }
    // Read once per episode: what is learned while watching applies from the next episode.
    val learned = remember(index) { item?.takeIf { it.kind == Kind.EPISODE }?.let(learnedIntro) }
    val skip = if (isLive) null else skipActionAt(position, duration, segments, learned, item?.kind == Kind.EPISODE)
    fun doSkip() {
        val target = skip?.targetMs ?: return
        player.seekTo(target)
        position = target
    }

    // ---------- Next episode (at the start of the credits) ----------
    // From the database if known, otherwise a guess; always long enough for the countdown.
    val creditsAt = creditsLeadMs(duration, segments, minLeadMs = autoNextMs + 5_000)
    var dismissedIndex by remember { mutableIntStateOf(-1) }
    val showNextPrompt = nextItem != null && duration > 0 && duration - position <= creditsAt && dismissedIndex != index

    // ---------- Visibility ----------
    // Series intro (Netflix style): when opening a series, first the info panel; the episode is already playing.
    var introVisible by remember { mutableStateOf(queue.intro != null) }
    var visible by remember { mutableStateOf(queue.intro == null) }
    var interaction by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    // Left/right with hidden controls: show them with focus on the seek bar, so pressing further keeps seeking.
    var focusSeekBar by remember { mutableStateOf(false) }
    fun showAtSeekBar() {
        focusSeekBar = true
        visible = true
        interaction++
    }

    fun show() {
        visible = true
        interaction++
    }

    // Live: after zapping with hidden controls, show the channel number and name for a few seconds.
    var zapTick by remember { mutableIntStateOf(0) }
    var zapInfoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(zapTick) {
        if (zapTick == 0) return@LaunchedEffect
        zapInfoVisible = true
        delay(ZAP_INFO_MS)
        zapInfoVisible = false
    }
    fun zap(next: Boolean) {
        if (next) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
        zapTick++
    }

    LaunchedEffect(visible) { onControlsVisible(visible) }
    LaunchedEffect(visible, interaction, isPlaying, panel) {
        if (visible && isPlaying && panel == Panel.NONE) {
            delay(HIDE_AFTER_MS)
            visible = false
        }
    }
    LaunchedEffect(visible, introVisible) {
        if (introVisible) return@LaunchedEffect // the info panel sets the focus itself
        if (visible) (if (focusSeekBar) seekFocus else playFocus).requestSafely() else rootFocus.requestSafely()
        focusSeekBar = false
    }
    // The info panel disappears after 10 seconds without action; the episode just keeps playing.
    LaunchedEffect(introVisible, interaction, panel) {
        if (introVisible && panel == Panel.NONE) {
            delay(INTRO_MS)
            introVisible = false
        }
    }

    BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE; if (!introVisible) show() }
    BackHandler(enabled = visible && panel == Panel.NONE && !introVisible) { visible = false }
    BackHandler(enabled = !visible && !introVisible) { onExit() }
    BackHandler(enabled = introVisible && panel == Panel.NONE) { onExit() }

    // Update seeking and the shown times right away (not only at the next poll), so the
    // remaining time is correct immediately.
    fun seekBy(delta: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        val target = (player.currentPosition + delta).coerceIn(0, max)
        player.seekTo(target)
        position = target
    }

    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause -> { togglePlay(); show(); return@onPreviewKeyEvent true }
                    Key.MediaPlay -> { player.play(); return@onPreviewKeyEvent true }
                    Key.MediaPause -> { player.pause(); show(); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { player.seekForward(); show(); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { player.seekBack(); show(); return@onPreviewKeyEvent true }
                }
                if (visible || showNextPrompt || introVisible || panel != Panel.NONE) {
                    interaction++
                    return@onPreviewKeyEvent false // let focus move within the controls or the next button
                }
                // Controls hidden:
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> if (isLive && queue.items.size > 1) zap(next = false) else show()
                    Key.DirectionDown, Key.ChannelDown -> if (isLive && queue.items.size > 1) zap(next = true) else show()
                    Key.DirectionLeft -> if (!isLive) { seekBy(-10_000); showAtSeekBar() }
                    Key.DirectionRight -> if (!isLive) { seekBy(10_000); showAtSeekBar() }
                    // During the intro OK skips it (the button looks focused); otherwise it shows the controls.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (skip != null) doSkip() else show()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Dark gradients at the top and bottom, so buttons and text stay readable.
                Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Palette.scrim, Color.Transparent))))
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(320.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.scrim))),
                )

                // Top left: back, 10 s back, next episode.
                Row(Modifier.padding(start = 48.dp, top = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, S.back, onClick = onExit)
                    if (!isLive) RoundIconButton(Icons.Filled.Replay10, S.rewind10, onClick = { player.seekBack() })
                    if (hasNext) RoundIconButton(Icons.Filled.SkipNext, S.nextEpisode, onClick = onNextEpisode)
                }

                // Top right: title and episode.
                Column(
                    Modifier.align(Alignment.TopEnd).padding(end = 56.dp, top = 40.dp).widthIn(max = 640.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(item?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item?.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Bottom: play button, seek bar, times and the buttons for audio/subtitles.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 56.dp, end = 56.dp, bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayPauseButton(isPlaying, onClick = { togglePlay() }, modifier = Modifier.focusRequester(playFocus))
                        Spacer(Modifier.width(28.dp))
                        if (isLive) {
                            Text(
                                "LIVE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Palette.text,
                                modifier = Modifier.background(Palette.live, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        } else {
                            Text(formatTime(position), style = MaterialTheme.typography.titleMedium, color = Palette.text, modifier = Modifier.width(96.dp))
                            SeekBar(
                                position = position,
                                buffered = buffered,
                                duration = duration,
                                onSeek = { delta ->
                                    seekBy(delta)
                                    interaction++
                                },
                                modifier = Modifier.weight(1f).focusRequester(seekFocus),
                            )
                            Text(
                                formatTime((duration - position).coerceAtLeast(0)),
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.text,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp)) // room for the time label under the knob
                    Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(trackSummary(tracks), checked = true, onClick = { panel = Panel.TRACKS })
                        Pill(S.otherTracks, onClick = { panel = Panel.TRACKS })
                        // Faster or slower playback isn't possible for live: the buffer then runs empty or falls behind.
                        if (!isLive) RoundIconButton(Icons.Outlined.Settings, S.playbackSpeed, small = true, onClick = { panel = Panel.SPEED })
                    }
                }
            }
        }

        if (isLive && zapInfoVisible && !visible && item != null) {
            ZapInfo(item, number = index + 1, modifier = Modifier.align(Alignment.BottomStart))
        }

        val intro = queue.intro
        if (introVisible && intro != null && panel == Panel.NONE) {
            SeriesIntroOverlay(
                intro = intro,
                isFavorite = isFavorite,
                onPlay = { introVisible = false },
                onEpisodes = onMoreEpisodes,
                onTracks = { panel = Panel.TRACKS },
                onFavorite = onToggleFavorite,
            )
        }

        if (skip != null && !showNextPrompt && panel == Panel.NONE && !introVisible) {
            SkipButton(
                text = if (skip.type == SegmentType.RECAP) S.skipRecap else S.skipIntro,
                pressedByOk = !visible,
                onClick = { doSkip(); interaction++ },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = if (visible) 190.dp else 56.dp),
            )
        }

        if (showNextPrompt && nextItem != null && panel == Panel.NONE && !introVisible) {
            // A new prompt per episode, so the countdown never carries over to the next one.
            key(index) {
                NextEpisodePrompt(
                    next = nextItem,
                    counting = isPlaying,
                    countdownMs = autoNextMs,
                    onPlay = onNextEpisode,
                    onDismiss = { dismissedIndex = index; show() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = if (visible) 190.dp else 56.dp),
                )
            }
        }

        when (panel) {
            Panel.TRACKS -> TracksPanel(player, tracks, onClose = { panel = Panel.NONE })
            Panel.SPEED -> SpeedPanel(speed, onSelect = { player.setPlaybackSpeed(it); panel = Panel.NONE })
            Panel.NONE -> Unit
        }
    }
}
