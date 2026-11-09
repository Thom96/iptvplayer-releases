package nl.iptvplayer.app.player

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import nl.iptvplayer.app.i18n.S
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.iptvplayer.app.ui.Palette
import nl.iptvplayer.app.ui.requestSafely
import nl.iptvplayer.app.ui.tapToClick

// Layers over the player's picture: zap info, series intro and "next episode".

// ---------- Zapping ----------

internal const val ZAP_INFO_MS = 4_000L

/** At the bottom after zapping: number and name of the channel, in the style of the Live tab full screen. */
@Composable
internal fun ZapInfo(item: PlayItem, number: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.scrim)))
            .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), style = MaterialTheme.typography.displaySmall, color = Palette.textSoft)
        Spacer(Modifier.width(28.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------- Series intro ----------

internal const val INTRO_MS = 10_000L

/** On the left a dark panel with title, info and buttons; on the right the episode is already playing. */
@Composable
internal fun SeriesIntroOverlay(
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
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
            contentColor = Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(50)),
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

// ---------- Next episode ----------

/**
 * Button at the bottom right at the start of the credits. The button fills up in [countdownMs] and then
 * starts the next episode by itself (only while playing). "Watch credits" cancels. With [countdownMs] 0
 * (set in Settings) nothing starts by itself.
 */
@Composable
internal fun NextEpisodePrompt(
    next: PlayItem,
    counting: Boolean,
    countdownMs: Long,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val auto = countdownMs > 0
    var elapsed by remember { mutableLongStateOf(0L) }
    var fired by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestSafely() }
    LaunchedEffect(counting) {
        if (!auto) return@LaunchedEffect
        while (counting && elapsed < countdownMs) {
            delay(100)
            elapsed += 100
        }
        // Only once: a restart of this effect (buffering) must not skip another episode.
        if (elapsed >= countdownMs && !fired) {
            fired = true
            onPlay()
        }
    }
    val seconds = ((countdownMs - elapsed + 999) / 1000).coerceAtLeast(0)

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
                // Fills up with champagne during the countdown: that way you see when it starts by itself.
                if (auto) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .fillMaxWidth(elapsed.toFloat() / countdownMs)
                            .background(Palette.accent.copy(alpha = 0.45f)),
                    )
                }
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(S.nextEpisode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        next.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text(if (auto && counting) S.seconds(seconds.toInt()) else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Pill(S.watchCredits, onClick = onDismiss)
    }
}

// ---------- Skip intro / recap ----------

/**
 * "Skip intro" at the bottom right, during the intro or recap. With hidden controls, OK presses it
 * (then it looks focused); with visible controls it is a normal button.
 */
@Composable
internal fun SkipButton(text: String, pressedByOk: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (pressedByOk) Palette.text else Palette.glass,
            contentColor = if (pressedByOk) Palette.background else Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (pressedByOk) androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = shape) else androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SkipNext, null, Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
