package nl.iptvplayer.app.player

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import nl.iptvplayer.app.i18n.S
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.iptvplayer.app.ui.Palette
import nl.iptvplayer.app.ui.formatTime
import nl.iptvplayer.app.ui.tapToClick

// Buttons and seek bar of the player controls.

// ---------- Parts ----------

@Composable
internal fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, small: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(if (small) 44.dp else 52.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Icon(icon, label, Modifier.align(Alignment.Center).size(if (small) 24.dp else 30.dp))
    }
}

/**
 * Play button in the same style as the other round buttons, but bigger: without focus a
 * translucent circle with a white icon, with focus completely filled with a dark icon. The
 * play icon shifts slightly to the right so the triangle looks centred, and
 * pause/play switch with a short transition.
 */
@Composable
internal fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.glass,
            contentColor = Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Crossfade(isPlaying, Modifier.align(Alignment.Center), label = "playPause") { playing ->
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) S.pause else S.play,
                Modifier.size(32.dp).offset(x = if (playing) 0.dp else 2.dp),
            )
        }
    }
}

/**
 * Seek bar in one Canvas: grey background, lighter buffered strip, played part in the accent colour and
 * a knob of fixed size. Without focus it is a thin bar with a small white knob; with focus
 * it gets thicker, the knob gets the accent colour and the time is shown below. Left/right seeks, and the longer
 * you hold the button, the bigger the step (see [seekStep]).
 */
@Composable
internal fun SeekBar(position: Long, buffered: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(fraction, 1f) else 0f
    val barHeight by animateDpAsState(if (focused) 8.dp else 4.dp, label = "barHeight")
    val thumbRadius by animateDpAsState(if (focused) 12.dp else 7.dp, label = "thumbRadius")
    val thumbColor by animateColorAsState(if (focused) Palette.accent else Palette.text, label = "thumbColor")
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = Palette.text, fontWeight = FontWeight.SemiBold)
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
        // Room for half the knob on both sides, so it isn't cut off at 0:00 and at the end.
        val left = r
        val width = (size.width - 2 * r).coerceAtLeast(0f)
        val cy = size.height / 2
        val corner = CornerRadius(h / 2, h / 2)
        fun bar(to: Float, color: Color) {
            if (to <= 0f) return
            drawRoundRect(color, topLeft = Offset(left, cy - h / 2), size = Size(width * to, h), cornerRadius = corner)
        }
        bar(1f, Palette.track)
        bar(bufferedFraction, Palette.trackBuffered)
        bar(fraction, Palette.accent)
        val cx = left + width * fraction
        if (focused) drawCircle(Palette.text, radius = r + 2.dp.toPx(), center = Offset(cx, cy))
        drawCircle(thumbColor, radius = r, center = Offset(cx, cy))

        // Time under the knob while seeking. Deliberately outside the Canvas (not clipped);
        // the Spacer under the bar keeps room for it.
        if (focused) {
            val text = textMeasurer.measure(formatTime(position), labelStyle)
            val padX = 8.dp.toPx()
            val padY = 3.dp.toPx()
            val w = text.size.width + 2 * padX
            val bh = text.size.height + 2 * padY
            val x = (cx - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val y = cy + r + 8.dp.toPx()
            drawRoundRect(Palette.badge, topLeft = Offset(x, y), size = Size(w, bh), cornerRadius = CornerRadius(bh / 2, bh / 2))
            drawText(text, topLeft = Offset(x + padX, y + padY))
        }
    }
}

/**
 * Seek step for left/right: one press is 10 s; holding speeds up to 30 s and then 60 s per
 * repeat. [repeatCount] comes from the remote control's repeated KeyDown events.
 */
private fun seekStep(repeatCount: Int): Long = when {
    repeatCount < 10 -> 10_000
    repeatCount < 30 -> 30_000
    else -> 60_000
}

@Composable
internal fun Pill(text: String, onClick: () -> Unit, checked: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.glass,
            contentColor = Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(50)),
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
