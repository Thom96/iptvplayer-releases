package nl.iptvplayer.app.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Buffer-instellingen voor snel starten: afspelen zodra er 1 s video binnen is (standaard 2,5 s),
 * na haperen 2 s. Daarna bouwt de buffer gewoon op tot 50 s.
 */
@OptIn(UnstableApi::class)
fun fastStartLoadControl(): LoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
        .build()
