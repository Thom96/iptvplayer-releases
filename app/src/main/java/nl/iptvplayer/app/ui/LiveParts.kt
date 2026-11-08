package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.EpgEntry
import nl.iptvplayer.app.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Onderdelen van de Live-tab: zenderregel, gids, info op volledig scherm.

private val clock = SimpleDateFormat("HH:mm", Locale.ROOT)
internal fun hhmm(ms: Long) = clock.format(Date(ms))

@Composable
internal fun ChannelRow(
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
internal fun ThinProgress(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(Palette.track, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Palette.accent, RoundedCornerShape(2.dp)))
    }
}

/** Gids onder het voorbeeld: nu en straks. */
@Composable
internal fun EpgPanel(vm: AppViewModel, channel: MediaItem, now: Long) {
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
internal fun FullscreenInfo(channel: MediaItem, epg: List<EpgEntry>, now: Long, index: Int) {
    val current = epg.firstOrNull { it.isNow(now) }
    val next = epg.firstOrNull { it.start >= (current?.end ?: now) }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.scrim)))
                .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (channel.num > 0) channel.num else index + 1).toString(),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.textSoft,
            )
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.title, style = MaterialTheme.typography.headlineSmall, color = Palette.text)
                if (current != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${current.title}  ·  ${hhmm(current.start)} – ${hhmm(current.end)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(420.dp)) { ThinProgress(current.fraction(now)) }
                }
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(S.laterAt(hhmm(next.start), next.title), style = MaterialTheme.typography.bodyMedium, color = Palette.textSoft)
                }
            }
            Text(S.zapHint, style = MaterialTheme.typography.labelMedium, color = Palette.textMuted)
        }
    }
}

@Composable
internal fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, textAlign = TextAlign.Center)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
