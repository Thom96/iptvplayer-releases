package nl.iptvplayer.app.player

import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.AppLanguage
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.ui.Palette
import nl.iptvplayer.app.ui.formatTime
import nl.iptvplayer.app.ui.requestSafely
import nl.iptvplayer.app.ui.tapToClick
import java.util.Locale

private const val HIDE_AFTER_MS = 4_000L

private enum class Panel { NONE, TRACKS, SPEED }

/** Eén keuze in het audio- of ondertitelingsmenu. */
private class TrackOption(val group: Tracks.Group?, val index: Int, val label: String, val selected: Boolean)

/**
 * Bediening tijdens het afspelen, in de stijl van Netflix met de kleuren van het logo:
 * linksboven terug / 10 s terug / volgende, rechtsboven de titel, onderin afspeelknop, tijdbalk
 * en een balk met audio en ondertiteling.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    player: ExoPlayer,
    queue: PlaybackQueue,
    onExit: () -> Unit,
    onControlsVisible: (Boolean) -> Unit,
    /** Naar de volgende aflevering; de activity slaat de huidige eerst als uitgekeken op. */
    onNextEpisode: () -> Unit,
    /** Serie-intro: S.episodesAndMore opent de seriepagina. */
    onMoreEpisodes: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    // ---------- Status van de speler ----------
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
                // Niet wachten op de volgende meting: met de tijden van de vorige aflevering zou de
                // "volgende aflevering"-knop blijven staan en nog een keer doorspringen.
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

    // ---------- Volgende aflevering (vlak voor de aftiteling) ----------
    // De provider geeft niet door waar de aftiteling begint: laatste 5% (min. 30 s, max. 90 s).
    val creditsAt = (duration * 0.05).toLong().coerceIn(30_000, 90_000)
    var dismissedIndex by remember { mutableIntStateOf(-1) }
    val showNextPrompt = nextItem != null && duration > 0 && duration - position <= creditsAt && dismissedIndex != index

    // ---------- Zichtbaarheid ----------
    // Serie-intro (Netflix-stijl): bij openen van een serie eerst het infopaneel, de aflevering speelt al.
    var introVisible by remember { mutableStateOf(queue.intro != null) }
    var visible by remember { mutableStateOf(queue.intro == null) }
    var interaction by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    // Links/rechts bij verborgen bediening: tonen met focus op de tijdbalk, zodat verder drukken blijft spoelen.
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

    LaunchedEffect(visible) { onControlsVisible(visible) }
    LaunchedEffect(visible, interaction, isPlaying, panel) {
        if (visible && isPlaying && panel == Panel.NONE) {
            delay(HIDE_AFTER_MS)
            visible = false
        }
    }
    LaunchedEffect(visible, introVisible) {
        if (introVisible) return@LaunchedEffect // het infopaneel zet zelf de focus
        if (visible) (if (focusSeekBar) seekFocus else playFocus).requestSafely() else rootFocus.requestSafely()
        focusSeekBar = false
    }
    // Infopaneel verdwijnt na 10 seconden zonder actie; de aflevering speelt gewoon door.
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
                    return@onPreviewKeyEvent false // focus binnen de bediening of de volgende-knop laten bewegen
                }
                // Bediening verborgen:
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> if (isLive && queue.items.size > 1) player.seekToPreviousMediaItem() else show()
                    Key.DirectionDown, Key.ChannelDown -> if (isLive && queue.items.size > 1) player.seekToNextMediaItem() else show()
                    Key.DirectionLeft -> if (!isLive) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); showAtSeekBar() }
                    Key.DirectionRight -> if (!isLive) { player.seekTo(player.currentPosition + 10_000); showAtSeekBar() }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> show()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Donkere verlopen boven en onder, zodat knoppen en tekst leesbaar blijven.
                Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(320.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))),
                )

                // Linksboven: terug, 10 s terug, volgende aflevering.
                Row(Modifier.padding(start = 48.dp, top = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, S.back, onClick = onExit)
                    if (!isLive) RoundIconButton(Icons.Filled.Replay10, S.rewind10, onClick = { player.seekBack() })
                    if (hasNext) RoundIconButton(Icons.Filled.SkipNext, S.nextEpisode, onClick = onNextEpisode)
                }

                // Rechtsboven: titel en aflevering.
                Column(
                    Modifier.align(Alignment.TopEnd).padding(end = 56.dp, top = 40.dp).widthIn(max = 640.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(item?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item?.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Onderin: afspeelknop, tijdbalk, tijden en de knoppen voor audio/ondertiteling.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 56.dp, end = 56.dp, bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayPauseButton(isPlaying, onClick = { togglePlay() }, modifier = Modifier.focusRequester(playFocus))
                        Spacer(Modifier.width(28.dp))
                        if (isLive) {
                            Text(
                                "LIVE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.background(Palette.live, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        } else {
                            Text(formatTime(position), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.width(96.dp))
                            SeekBar(
                                position = position,
                                buffered = buffered,
                                duration = duration,
                                onSeek = { delta ->
                                    val target = (player.currentPosition + delta).coerceIn(0, duration.coerceAtLeast(0))
                                    player.seekTo(target)
                                    position = target
                                    interaction++
                                },
                                modifier = Modifier.weight(1f).focusRequester(seekFocus),
                            )
                            Text(
                                formatTime((duration - position).coerceAtLeast(0)),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp)) // ruimte voor het tijdlabel onder de bol
                    Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(trackSummary(tracks), checked = true, onClick = { panel = Panel.TRACKS })
                        Pill(S.otherTracks, onClick = { panel = Panel.TRACKS })
                        RoundIconButton(Icons.Outlined.Settings, S.playbackSpeed, small = true, onClick = { panel = Panel.SPEED })
                    }
                }
            }
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

        if (showNextPrompt && nextItem != null && panel == Panel.NONE && !introVisible) {
            // Per aflevering een nieuwe prompt, zodat de afteltijd nooit meegaat naar de volgende.
            key(index) {
                NextEpisodePrompt(
                    next = nextItem,
                    counting = isPlaying,
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

// ---------- Serie-intro ----------

private const val INTRO_MS = 10_000L

/** Links een donker paneel met titel, info en knoppen; rechts speelt de aflevering al. */
@Composable
private fun SeriesIntroOverlay(
    intro: SeriesIntro,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onEpisodes: () -> Unit,
    onTracks: () -> Unit,
    onFavorite: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestSafely() }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Palette.background.copy(alpha = 0.96f),
                    0.42f to Palette.background.copy(alpha = 0.85f),
                    0.72f to Color.Transparent,
                ),
            ),
    ) {
        Column(
            Modifier.fillMaxHeight().width(640.dp).padding(start = 64.dp, top = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(intro.title, style = MaterialTheme.typography.displaySmall, color = Palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text(intro.meta, style = MaterialTheme.typography.titleSmall, color = Palette.textMuted)
            intro.plot?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            intro.cast?.let {
                Spacer(Modifier.height(8.dp))
                Text(S.cast(it), style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(26.dp))
            IntroButton(Icons.Filled.PlayArrow, intro.playLabel, onPlay, Modifier.focusRequester(playFocus))
            IntroButton(Icons.Outlined.VideoLibrary, S.episodesAndMore, onEpisodes)
            IntroButton(Icons.Outlined.Subtitles, S.audioAndSubtitles, onTracks)
            IntroButton(
                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (isFavorite) S.inFavorites else S.addToFavorites,
                onFavorite,
            )
        }
    }
}

@Composable
private fun IntroButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(vertical = 3.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------- Volgende aflevering ----------

private const val NEXT_COUNTDOWN_MS = 5_000L

/**
 * Knop rechtsonder vlak voor de aftiteling. De knop loopt in 5 seconden vol en start dan
 * vanzelf de volgende aflevering (alleen terwijl er wordt afgespeeld). "Aftiteling kijken" annuleert.
 */
@Composable
private fun NextEpisodePrompt(
    next: PlayItem,
    counting: Boolean,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var fired by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestSafely() }
    LaunchedEffect(counting) {
        while (counting && elapsed < NEXT_COUNTDOWN_MS) {
            delay(100)
            elapsed += 100
        }
        // Maar één keer: een herstart van dit effect (bufferen) mag niet nog een aflevering overslaan.
        if (elapsed >= NEXT_COUNTDOWN_MS && !fired) {
            fired = true
            onPlay()
        }
    }
    val seconds = ((NEXT_COUNTDOWN_MS - elapsed + 999) / 1000).coerceAtLeast(0)

    Column(modifier.width(380.dp), horizontalAlignment = Alignment.End) {
        Surface(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().focusRequester(playFocus).tapToClick(onPlay),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.text,
                contentColor = Palette.background,
                focusedContainerColor = Palette.text,
                focusedContentColor = Palette.background,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(12.dp)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        ) {
            Box {
                // Vult zich in 5 seconden met geel: zo zie je wanneer hij vanzelf start.
                Box(
                    Modifier
                        .matchParentSize()
                        .fillMaxWidth(elapsed.toFloat() / NEXT_COUNTDOWN_MS)
                        .background(Palette.accent.copy(alpha = 0.45f)),
                )
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(S.nextEpisode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        next.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text(if (counting) "${seconds}s" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Pill(S.watchCredits, onClick = onDismiss)
    }
}

// ---------- Onderdelen ----------

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, small: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(if (small) 44.dp else 52.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Icon(icon, label, Modifier.align(Alignment.Center).size(if (small) 24.dp else 30.dp))
    }
}

/** Grote ronde afspeelknop: crème met donker icoon, met een gele rand bij focus. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(60.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.text,
            contentColor = Palette.background,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(4.dp, Palette.accent), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) S.pause else S.play,
            Modifier.align(Alignment.Center).size(34.dp),
        )
    }
}

/**
 * Tijdbalk in één Canvas: grijze achtergrond, lichtere gebufferde strook, afgespeeld deel in de accentkleur en
 * een bol met vaste grootte. Zonder focus is het een dunne balk met een kleine witte bol; met focus
 * wordt hij dikker, krijgt de bol de accentkleur en staat de tijd eronder. Links/rechts spoelt, en hoe langer
 * je de knop vasthoudt, hoe groter de stap (zie [seekStep]).
 */
@Composable
private fun SeekBar(position: Long, buffered: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(fraction, 1f) else 0f
    val barHeight by animateDpAsState(if (focused) 8.dp else 4.dp, label = "barHeight")
    val thumbRadius by animateDpAsState(if (focused) 12.dp else 7.dp, label = "thumbRadius")
    val thumbColor by animateColorAsState(if (focused) Palette.accent else Color.White, label = "thumbColor")
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
    Canvas(
        modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSeek(-seekStep(e.nativeKeyEvent.repeatCount)); true }
                    Key.DirectionRight -> { onSeek(seekStep(e.nativeKeyEvent.repeatCount)); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val r = thumbRadius.toPx()
        val h = barHeight.toPx()
        // Ruimte voor de halve bol aan beide kanten, zodat hij op 0:00 en aan het eind niet wordt afgesneden.
        val left = r
        val width = (size.width - 2 * r).coerceAtLeast(0f)
        val cy = size.height / 2
        val corner = CornerRadius(h / 2, h / 2)
        fun bar(to: Float, color: Color) {
            if (to <= 0f) return
            drawRoundRect(color, topLeft = Offset(left, cy - h / 2), size = Size(width * to, h), cornerRadius = corner)
        }
        bar(1f, Color(0x40FFFFFF))
        bar(bufferedFraction, Color(0x66FFFFFF))
        bar(fraction, Palette.accent)
        val cx = left + width * fraction
        if (focused) drawCircle(Color.White, radius = r + 2.dp.toPx(), center = Offset(cx, cy))
        drawCircle(thumbColor, radius = r, center = Offset(cx, cy))

        // Tijd onder de bol tijdens het spoelen. Valt bewust buiten de Canvas (niet afgeknipt);
        // de Spacer onder de balk houdt daar ruimte voor vrij.
        if (focused) {
            val text = textMeasurer.measure(formatTime(position), labelStyle)
            val padX = 8.dp.toPx()
            val padY = 3.dp.toPx()
            val w = text.size.width + 2 * padX
            val bh = text.size.height + 2 * padY
            val x = (cx - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val y = cy + r + 8.dp.toPx()
            drawRoundRect(Color(0xCC000000), topLeft = Offset(x, y), size = Size(w, bh), cornerRadius = CornerRadius(bh / 2, bh / 2))
            drawText(text, topLeft = Offset(x + padX, y + padY))
        }
    }
}

/**
 * Spoelstap bij links/rechts: één druk is 10 s; vasthouden versnelt naar 30 s en daarna 60 s per
 * herhaling. [repeatCount] komt van de herhaalde KeyDown-events van de afstandsbediening.
 */
private fun seekStep(repeatCount: Int): Long = when {
    repeatCount < 10 -> 10_000
    repeatCount < 30 -> 30_000
    else -> 60_000
}

@Composable
private fun Pill(text: String, onClick: () -> Unit, checked: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (checked) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

// ---------- Audio en ondertiteling ----------


private fun label(format: Format, fallback: String): String {
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(appLocale).replaceFirstChar { c -> c.uppercase(appLocale) } }
    return listOfNotNull(language, format.label?.takeIf { it.isNotBlank() && it != language })
        .joinToString(" · ")
        .ifEmpty { fallback }
}

private fun options(tracks: Tracks, type: Int, prefix: String): List<TrackOption> {
    var n = 0
    return tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            n++
            TrackOption(g, i, label(g.getTrackFormat(i), "$prefix $n"), g.isTrackSelected(i))
        }
    }
}

/** Bijvoorbeeld "Engels met ondertiteling" of "Nederlands". */
private fun trackSummary(tracks: Tracks): String {
    val audio = options(tracks, C.TRACK_TYPE_AUDIO, S.audio).firstOrNull { it.selected }?.label ?: S.defaultAudio
    val sub = options(tracks, C.TRACK_TYPE_TEXT, S.subtitles).firstOrNull { it.selected }
    return if (sub != null) S.withSubtitles(audio, sub.label) else audio
}

@OptIn(UnstableApi::class)
@Composable
private fun TracksPanel(player: ExoPlayer, tracks: Tracks, onClose: () -> Unit) {
    val audio = options(tracks, C.TRACK_TYPE_AUDIO, S.audio)
    val subs = options(tracks, C.TRACK_TYPE_TEXT, S.subtitles)
    val subsOff = subs.none { it.selected }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestSafely() }

    fun select(type: Int, o: TrackOption) {
        val g = o.group ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, o.index))
            .build()
    }

    SidePanel {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f)) {
                PanelHeading(S.audio)
                if (audio.isEmpty()) PanelNote(S.noChoice)
                LazyColumn {
                    items(audio.size) { i ->
                        val o = audio[i]
                        PanelOption(o.label, o.selected, onClick = { select(C.TRACK_TYPE_AUDIO, o) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                PanelHeading(S.subtitles)
                LazyColumn {
                    item {
                        PanelOption(
                            S.off, subsOff,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            },
                            modifier = if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    items(subs) { o -> PanelOption(o.label, o.selected, onClick = { select(C.TRACK_TYPE_TEXT, o) }) }
                }
            }
        }
    }
}

@Composable
private fun SpeedPanel(current: Float, onSelect: (Float) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestSafely() }
    SidePanel(width = 360) {
        Column {
            PanelHeading(S.playbackSpeed)
            listOf(0.75f, 1f, 1.25f, 1.5f).forEachIndexed { i, s ->
                val label = if (s == 1f) S.normalSpeed else "${s}x".let { if (I18n.language == AppLanguage.EN) it else it.replace('.', ',') }
                PanelOption(label, current == s, onClick = { onSelect(s) }, modifier = if (i == 1) Modifier.focusRequester(first) else Modifier)
            }
        }
    }
}

@Composable
private fun SidePanel(width: Int = 720, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(width.dp)
                .background(Palette.background)
                .padding(horizontal = 40.dp, vertical = 48.dp),
        ) { content() }
    }
}

@Composable
private fun PanelHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun PanelNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted)
}

@Composable
private fun PanelOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) Color.White else Palette.textMuted,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp)) {
                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = Palette.accent)
            }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
