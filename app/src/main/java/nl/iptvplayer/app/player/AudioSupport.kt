package nl.iptvplayer.app.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

// Sound that cheap boxes often can't play (Dolby, DTS): choose what works, and say it when nothing does.

/**
 * Renderers for the player. Another decoder is tried when the first one fails to start. With
 * [passthrough] off the sound never goes to the TV as Dolby/DTS: some TVs and soundbars say they can
 * play it but stay silent.
 */
@OptIn(UnstableApi::class)
fun playerRenderersFactory(context: Context, passthrough: Boolean): DefaultRenderersFactory =
    object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
            if (passthrough) return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
            @Suppress("DEPRECATION")
            return DefaultAudioSink.Builder(context)
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
    }.setEnableDecoderFallback(true)

/**
 * The name of the sound format when this device can't play any audio track of the video (e.g. "Dolby
 * Digital"), otherwise null. The player already picks a playable track by itself when there is one.
 */
@OptIn(UnstableApi::class)
fun unsupportedAudioName(tracks: Tracks): String? {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
    if (audio.isEmpty()) return null
    if (audio.any { g -> (0 until g.length).any { g.isTrackSupported(it) } }) return null
    val format = audio.firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let(g::getTrackFormat) }
        ?: audio.first().getTrackFormat(0)
    return audioFormatName(format.sampleMimeType, format.codecs)
}

/** Readable name of a sound format, for the notice. */
fun audioFormatName(mime: String?, codecs: String? = null): String = when (mime) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
    MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
    MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
    MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_DTS_X -> "DTS:X"
    MimeTypes.AUDIO_MPEG_L2 -> "MP2"
    else -> codecs?.takeIf { it.isNotBlank() }?.uppercase()
        ?: mime?.substringAfter('/')?.uppercase()
        ?: "?"
}
