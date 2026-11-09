package nl.iptvplayer.app.player.timeshift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.ui.Palette
import nl.iptvplayer.app.ui.formatTime

/** State of [timeshift] for [player], measured twice a second; null when the channel isn't recorded. */
@UnstableApi
@Composable
fun rememberTimeshiftState(timeshift: LiveTimeshift?, player: Player): State<LiveTimeshift.State?> {
    val state = remember(timeshift, player) { mutableStateOf<LiveTimeshift.State?>(null) }
    LaunchedEffect(timeshift, player) {
        if (timeshift == null) return@LaunchedEffect
        while (true) {
            state.value = timeshift.state(player)
            delay(500)
        }
    }
    return state
}

/**
 * LIVE (red) or "12:40 behind live", with a bar of the recorded window and where playback is in it.
 */
@UnstableApi
@Composable
fun TimeshiftBar(state: LiveTimeshift.State, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (state.isLive) {
            Text(
                "LIVE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.text,
                modifier = Modifier.background(Palette.live, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        } else {
            Text(
                S.behindLive(formatTime(state.behindMs)),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.text,
                modifier = Modifier.background(Palette.badge, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        val fraction = if (state.windowMs > 0) (state.positionMs.toFloat() / state.windowMs).coerceIn(0f, 1f) else 1f
        Box(Modifier.weight(1f).height(6.dp).background(Palette.track, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.accent, RoundedCornerShape(3.dp)))
        }
    }
}
