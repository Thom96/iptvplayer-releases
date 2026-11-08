package nl.iptvplayer.app.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Buffer settings for a quick start: play as soon as 1 s of video is in (default 2.5 s),
 * 2 s after a stall. After that the buffer builds up normally to 50 s.
 */
@OptIn(UnstableApi::class)
fun fastStartLoadControl(): LoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
        .build()
