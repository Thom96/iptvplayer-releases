package nl.iptvplayer.app.player

import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.appLocale
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.AppLanguage
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.iptvplayer.app.ui.Palette
import nl.iptvplayer.app.ui.requestSafely
import nl.iptvplayer.app.ui.tapToClick
import java.util.Locale

// Zijpanelen van de speler: audio en ondertiteling, afspeelsnelheid.

/** Eén keuze in het audio- of ondertitelingsmenu. */
private class TrackOption(val group: Tracks.Group?, val index: Int, val label: String, val selected: Boolean)

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
internal fun trackSummary(tracks: Tracks): String {
    val audio = options(tracks, C.TRACK_TYPE_AUDIO, S.audio).firstOrNull { it.selected }?.label ?: S.defaultAudio
    val sub = options(tracks, C.TRACK_TYPE_TEXT, S.subtitles).firstOrNull { it.selected }
    return if (sub != null) S.withSubtitles(audio, sub.label) else audio
}

@OptIn(UnstableApi::class)
@Composable
internal fun TracksPanel(player: ExoPlayer, tracks: Tracks, onClose: () -> Unit) {
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
internal fun SpeedPanel(current: Float, onSelect: (Float) -> Unit) {
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
    Box(Modifier.fillMaxSize().background(Palette.scrimLight), contentAlignment = Alignment.CenterEnd) {
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
    Text(text, style = MaterialTheme.typography.headlineSmall, color = Palette.text, modifier = Modifier.padding(bottom = 16.dp))
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
            contentColor = if (selected) Palette.text else Palette.textMuted,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(10.dp)),
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
